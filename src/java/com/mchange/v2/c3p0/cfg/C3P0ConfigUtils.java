/*
 * Distributed as part of c3p0 v.0.9.5-pre1
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.cfg;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.cfg.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.impl.*;

public final class C3P0ConfigUtils
{
    public final static String PROPS_FILE_RSRC_PATH     = "/c3p0.properties";
    public final static String PROPS_FILE_PROP_PFX      = "c3p0.";
    public final static int    PROPS_FILE_PROP_PFX_LEN  = 5;

    private final static String[] MISSPELL_PFXS = {"/c3pO", "/c3po", "/C3P0", "/C3PO"}; 
    
    final static MLogger logger = MLog.getLogger( C3P0ConfigUtils.class );
    
    static
    {
        if ( logger.isLoggable(MLevel.WARNING) && C3P0ConfigUtils.class.getResource( PROPS_FILE_RSRC_PATH ) == null )
        {
            // warn on a misspelling... its an ugly way to do this, but since resources are not listable...
            for (int i = 0; i < MISSPELL_PFXS.length; ++i)
            {
                String test = MISSPELL_PFXS[i] + ".properties";
                if (C3P0ConfigUtils.class.getResource( MISSPELL_PFXS[i] + ".properties" ) != null)
                {
                    logger.warning("POSSIBLY MISSPELLED c3p0.properties CONFIG RESOURCE FOUND. " +
                                   "Please ensure the file name is c3p0.properties, all lower case, " +
                                   "with the digit 0 (NOT the letter O) in c3p0. It should be placed " +
                                   " in the top level of c3p0's effective classpath.");
                    break;
                }
            }
        }
    }

    public static HashMap extractHardcodedC3P0Defaults(boolean stringify)
    {
	HashMap out = new HashMap();

	try
	    {
		Method[] methods = C3P0Defaults.class.getMethods();
		for (int i = 0, len = methods.length; i < len; ++i)
		    {
			Method m = methods[i];
			int mods = m.getModifiers();
			if ((mods & Modifier.PUBLIC) != 0 && (mods & Modifier.STATIC) != 0 && m.getParameterTypes().length == 0)
			    {
				if (stringify)
				    {
					Object val = m.invoke( null, null );
					if ( val != null )
					    out.put( m.getName(), String.valueOf( val ) );
				    }
				else
				    out.put( m.getName(), m.invoke( null, null ) );
			    }
		    }
	    }
	catch (Exception e)
	    {
		logger.log( MLevel.WARNING, "Failed to extract hardcoded default config!?", e );
	    }

	return out;
    }

    public static HashMap extractHardcodedC3P0Defaults()
    { return extractHardcodedC3P0Defaults( true ); }

    public static HashMap extractC3P0PropertiesResources()
    {
	HashMap out = new HashMap();

// 	Properties props = findResourceProperties();
// 	props.putAll( findAllC3P0Properties() );

 	Properties props = findAllC3P0Properties();
	for (Iterator ii = props.keySet().iterator(); ii.hasNext(); )
	    {
		String key = (String) ii.next();
		String val = (String) props.get(key);
		if ( key.startsWith(PROPS_FILE_PROP_PFX) )
		    out.put( key.substring(PROPS_FILE_PROP_PFX_LEN).trim(), val.trim() );
	    }

	return out;
    }

    public static C3P0Config configFromFlatDefaults(HashMap flatDefaults)
    {
	NamedScope defaults = new NamedScope();
	defaults.props.putAll( flatDefaults );
	
	HashMap configNamesToNamedScopes = new HashMap();
	
	return new C3P0Config( defaults, configNamesToNamedScopes ); 
    }
    
    public static String getPropFileConfigProperty( String prop )
    { return MultiPropertiesConfig.readVmConfig().getProperty( prop ); }

    private static Properties findResourceProperties()
    { return MultiPropertiesConfig.readVmConfig().getPropertiesByResourcePath(PROPS_FILE_RSRC_PATH); }

    private static Properties findAllC3P0Properties()
    { return MultiPropertiesConfig.readVmConfig().getPropertiesByPrefix("c3p0"); }

    static Properties findAllC3P0SystemProperties()
    {
	Properties out = new Properties();

	try
	    {
		for (Iterator ii = C3P0Defaults.getKnownProperties().iterator(); ii.hasNext(); )
		    {
			String key = (String) ii.next();
			String prefixedKey = "c3p0." + key;
			String value = System.getProperty( prefixedKey );
			if (value != null && value.trim().length() > 0)
			    out.put( key, value );
		    }
	    }
	catch (SecurityException e)
	    { 
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"A SecurityException occurred while trying to read c3p0 System properties. " + 
				"c3p0 configuration set via System properties may be ignored!",
				e );
	    }

	return out;
    }

    /**
     * @return null if no per-user override is found
     */
    public static Object extractUserOverride(String propName, String userName, Map userOverrides)
    {
	Map specificUserOverrides = (Map) userOverrides.get( userName ); 
	if (specificUserOverrides != null)
	    return specificUserOverrides.get( propName );
	else
	    return null;
    }

    public static Boolean extractBooleanOverride(String propName, String userName, Map userOverrides)
    {
	Object check = extractUserOverride( propName, userName, userOverrides);
	if ( check == null || check instanceof Boolean )
	    return (Boolean) check;
	else if (check instanceof String)
	    return Boolean.valueOf( (String) check );
	else
	    throw new ClassCastException("Parameter '" + propName + "' as overridden for user '" + userName + "' is " + check + ", which cannot be converted to Boolean.");
    }

    private C3P0ConfigUtils()
    {}
}
