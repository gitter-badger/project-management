/******************************************************************************
 * Copyright (c) 2012 Masatomi KINO and others. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *      Masatomi KINO - initial API and implementation
 * $Id$
 ******************************************************************************/
//�쐬��: 2012/06/26

package nu.mine.kino.plugin.webrecorder.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * @author Masatomi KINO
 * @version $Revision$
 */
public class RecordFilter implements Filter {
    /**
     * Logger for this class
     */
    private static final Logger logger = Logger
            .getLogger(RecordFilter.class);

    @Override
    public void destroy() {
        // TODO �����������ꂽ���\�b�h�E�X�^�u

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        logger.debug("doFilter(ServletRequest, ServletResponse, FilterChain) - start");
        HttpServletResponse hResponse = (HttpServletResponse) response;
        HttpServletRequest hRequest = (HttpServletRequest) request;

        ServletResponse wrapResponse = new ResponseCaptureResponse(hRequest,
                hResponse);

        chain.doFilter(request, wrapResponse);

        logger.debug("doFilter(ServletRequest, ServletResponse, FilterChain) - end");
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
        // TODO �����������ꂽ���\�b�h�E�X�^�u

    }

}