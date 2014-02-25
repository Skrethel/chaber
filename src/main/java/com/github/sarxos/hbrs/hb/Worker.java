package com.github.sarxos.hbrs.hb;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Worker<T> implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Worker.class);

	private final class Runner extends Thread {

		public Runner(Runnable r, String name) {
			super(r, name);
			setDaemon(true);
		}
	}

	private final LinkedBlockingQueue<T> items;

	private final String name;
	private final Runner runner;
	private final boolean stateless;
	private final int capacity;

	private AtomicBoolean running = new AtomicBoolean(false);

	public Worker(String name) {
		this(name, false);
	}

	public Worker(String name, boolean start) {
		this(name, start, false);
	}

	public Worker(String name, boolean start, boolean stateless) {
		this(name, 0, start, stateless);
	}

	/**
	 * @param name the worker name
	 * @param start shall worker start immediately
	 * @param stateless is worker stateless (does not create Hibernate session)
	 */
	public Worker(String name, int capacity, boolean start, boolean stateless) {

		this.name = name;
		this.runner = new Runner(this, name);
		this.stateless = stateless;
		this.capacity = capacity;
		this.items = new LinkedBlockingQueue<T>(capacity > 0 ? capacity : Integer.MAX_VALUE);

		if (start) {
			start();
		}
	}

	public void start() {
		if (running.compareAndSet(false, true)) {
			runner.start();
		}
	}

	public void stop() {
		if (running.compareAndSet(true, false)) {
			LOG.info("Message persister has been stopped");
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	public void process(Collection<T> entities) {
		for (T entity : entities) {
			process(entity);
		}
	}

	public void process(T item) {

		if (!isRunning()) {
			throw new RuntimeException("Worker is not running");
		}

		int size = items.size();

		if (capacity > 0 && size > capacity * 0.95) {

			int drain = (int) (capacity * 0.25);

			LOG.error("Worker {} capacity problem detected!", name);
			LOG.error("Worker queue is almost completely excited ({}/{}), draining {} items", size, capacity, drain);

			for (int i = 0; i < drain; i++) {
				if (items.isEmpty()) {
					break;
				}
				try {
					items.take();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		try {
			this.items.put(item);
		} catch (InterruptedException e) {
			LOG.debug("Interrupted: " + e.getMessage(), e);
		}
	}

	public abstract void work(Session session, T entity);

	@Override
	public void run() {

		T m = null;

		int bs = PersistenceKeeper.getBatchSize();
		int c = 0;

		Transaction t = null;
		Session s = null;

		while (isRunning()) {

			if (items.isEmpty()) {

				if (!stateless) {

					if (t != null && t.isActive()) {
						try {
							t.commit();
						} catch (HibernateException e) {
							t.rollback();
							LOG.error(e.getMessage(), e);
						}
					}

					if (s != null && s.isOpen()) {
						try {
							s.flush();
							s.clear();
						} catch (HibernateException e) {
							s.clear();
							LOG.error(e.getMessage(), e);
						} finally {
							s.close();
						}
					}

					c = 0;
				}

				LOG.debug("All awaiting items has been worked out");
			}

			try {
				m = items.take();
			} catch (InterruptedException e) {
				LOG.debug("Message persister interrupted: " + e.getMessage(), e);
				return;
			}

			if (!stateless) {
				if (s == null || !s.isOpen()) {
					s = createSession();
					t = s.beginTransaction();
				}
			}

			try {
				work(s, m);
			} catch (HibernateException e) {

				LOG.error("Hibernate error", e);

				if (!stateless) {
					try {
						t.rollback();
					} catch (Exception e2) {
						LOG.error("Cannot rollback", e2);
					} finally {
						s.clear();
						s.close();
					}
				}

			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}

			if (!stateless) {
				if (c > 0 && c++ % bs == 0) {

					try {
						t.commit();
					} catch (HibernateException e) {
						t.rollback();
						LOG.error(e.getMessage(), e);
					}

					try {
						s.flush();
					} catch (HibernateException e) {
						LOG.error(e.getMessage(), e);
					} finally {
						s.clear();
					}

					t = s.beginTransaction();
				}
			}
		}
	}

	protected Session createSession() {
		return PersistenceKeeper.getSessionFactory().openSession();
	}
}