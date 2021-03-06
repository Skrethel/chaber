package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


/**
 * This is fallback exception mapper which simply means that it will be used
 * when there was no other appropriate mapper found to map the exception to
 * specific response. This mapper will always return HTTP 500.
 * 
 * @author Bartosz Firyn (sarxos)
 */
@Provider
public class FallbackExceptionMapper extends AbstractExceptionMapper<Throwable> {

	/**
	 * Yup, this is a logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(FallbackExceptionMapper.class);

	@Override
	public Response toResponse(Throwable exception) {
		LOG.error(exception.getMessage(), exception);
		return build(exception);
	}
}
