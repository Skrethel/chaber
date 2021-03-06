package com.github.sarxos.hbrs.rs.mapper;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.hibernate.exception.GenericJDBCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class GenericJDBCExceptionMapper extends AbstractExceptionMapper<GenericJDBCException> {

	private static final Logger LOG = LoggerFactory.getLogger(GenericJDBCExceptionMapper.class);

	@Override
	public Response toResponse(GenericJDBCException exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		LOG.error(exception.getMessage(), exception);

		Throwable t = exception;

		do {
			if ((t = t.getCause()) == null) {
				break;
			}
		} while (!(t instanceof SQLException));

		String message = null;
		String clazz = null;

		if (t == null) {
			message = exception.getMessage();
			clazz = exception.getClass().getName();
		} else {
			message = t.getMessage();
			clazz = t.getClass().getName();
		}

		Map<String, String> r = new HashMap<String, String>();
		r.put("exception", clazz);
		r.put("message", message);

		return Response
			.status(Status.BAD_REQUEST)
			.type(MediaType.APPLICATION_JSON)
			.entity(r)
			.build();
	}
}
