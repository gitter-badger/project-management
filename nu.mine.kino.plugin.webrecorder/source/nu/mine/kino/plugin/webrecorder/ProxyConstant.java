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
//作成日: 2012/06/04

package nu.mine.kino.plugin.webrecorder;

/**
 * @author Masatomi KINO
 * @version $Revision$
 */
public interface ProxyConstant {

    public static final String LINE_SEPARATOR = System
            .getProperty("line.separator");

    public static final String PORT = "port";

    public static final String CACHE_BASE_PATH = "cacheBasePath";

    public static final String TRIM_FLAG = "trimflag";

    public static final String TRIM_START_INDEX = "trimStartIndex";

    public static final String TRIM_LENGTH = "trimLength";

    public static final String POST_BODY_FLAG = "postBodyFlag";

    public static final String CONSOLE_ID = "Web Recorder";

    public static final String CONSOLE_REQ_RES_ID = "Web Recorder(req/res)";

    public static final String METHOD_GET = "GET";

    public static final String METHOD_POST = "POST";

    public static final String[] EXCEPT_EXTs = new String[] { ".png", ".jpg",
            ".gif", ".css", ".js", ".svg" }; // 情報の出力は不要なので除外
}
