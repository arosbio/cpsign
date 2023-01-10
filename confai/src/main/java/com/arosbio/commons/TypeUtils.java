/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Named;

public class TypeUtils {
	
	public static List<String> getNames(Object o){
		if (o instanceof Named) {
			if (o instanceof Aliased) {
				List<String> list = new ArrayList<>();
				list.add(((Named) o).getName());
				for (String s : ((Aliased) o).getAliases())
					list.add(s);
				return list;
			} else {
				return Arrays.asList(((Named) o).getName());
			}
		}
		return Arrays.asList(o.getClass().getSimpleName());
	}

	public static boolean isInt(Object obj) {
		try {
			asInt(obj);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static int asInt(Object obj) throws NumberFormatException {
		if (obj instanceof Integer) {
			return (int) obj;
		} else if (obj instanceof Long) {
			return ((Long) obj).intValue();
		} else if (obj instanceof String) {
			try {
				Long asLong = Long.parseLong((String)obj);
				return asLong.intValue();
			} catch (NumberFormatException e) {}
		} else if (obj instanceof BigDecimal) {
			try {
				return (int) ((BigDecimal) obj).longValueExact();
			} catch (ArithmeticException ae) {}
		}
		throw new NumberFormatException(obj + " is not an integer");
	}

	public static boolean isLong(Object obj) {
		try {
			asLong(obj);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static long asLong(Object obj) throws NumberFormatException {
		if (obj instanceof Integer) {
			return (int) obj;
		} else if (obj instanceof Long) {
			return (Long) obj;
		} else if (obj instanceof String) {
			try {
				return Long.parseLong((String)obj);
			} catch (NumberFormatException e) {}
		} else if (obj instanceof BigDecimal) {
			try {
				return ((BigDecimal) obj).longValueExact();
			} catch (ArithmeticException ae) {}
		}
		throw new NumberFormatException(obj + " is not a long");
	}

	public static boolean isDouble(Object obj) {
		try {
			asDouble(obj);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static double asDouble(Object obj) throws NumberFormatException {
		if (obj instanceof BigDecimal) {
			return ((BigDecimal) obj).doubleValue();
		} else if (obj instanceof Number) {
			return ((Number) obj).doubleValue();
		} else if (obj instanceof String) {
			try {
				return Double.parseDouble((String)obj);
			} catch (NumberFormatException e) {}
		}
		throw new NumberFormatException(String.format("'%s' is not a double",obj));
	}

	public static boolean isBoolean(Object obj) {
		try {
			asBoolean(obj);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static boolean asBoolean(Object obj) throws IllegalArgumentException {
		if (obj instanceof Boolean) {
			return (boolean) obj;
		} else if (obj instanceof String) {
			return parseBoolean((String)obj);
		}
		throw new IllegalArgumentException(String.format("'%s' is not a boolean",obj));
	}

	public static boolean parseBoolean(String input) throws IllegalArgumentException {
		if (input == null || input.trim().isEmpty())
			throw new IllegalArgumentException("Empty argument cannot be converted to boolean");
		String lowerCase = input.toLowerCase().trim();

		if (lowerCase.equals("t")|| lowerCase.equals("true")) {
			return true;
		} else if (lowerCase.equals("f") || lowerCase.equals("false")) {
			return false;
		}
		throw new IllegalArgumentException(String.format("'%s' is not a boolean",input));
	}
	
	public static char asChar(Object input) {
		if (input instanceof Character)
			return (char) input;
		if (input instanceof String) {
			String str = (String) input;
			
			if (str.length() > 1) {
				String trimmed = str.trim();
				if (trimmed.length()>1)
					throw new IllegalArgumentException("Input '"+str+"' cannot be converted to a single character") ;
				if (trimmed.isEmpty())
					throw new IllegalArgumentException("Input '"+str+"' is not formatted correctly - cannot be converted to a single character");
				return trimmed.charAt(0);
			}
			
			return str.charAt(0);
		}
		if (input instanceof Integer) {
			return (char)(int)input;
		}
		throw new IllegalArgumentException("Input '"+ input + "' cannot be converted to a single character");
	}

	public static boolean overridesEquals(Object clazz) {
		try {
			Method m = clazz.getClass().getMethod("equals",new Class[] {Object.class});
			return clazz.getClass() == m.getDeclaringClass() && clazz.getClass() != Object.class;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean objectIsOfType(Object obj, Class<?> type){
		return isOfType(obj.getClass(), type);
	}

	public static boolean objectIsOfType(Object obj, Class<?>... types){
		return isOfType(obj.getClass(), types);
	}
	
	public static boolean isOfType(Class<?> clazz, Class<?> type) {
		if (clazz == null)
			return false;
		if (clazz == type)
			return true;
		return type.isAssignableFrom(clazz);
	}

	public static boolean isOfType(Class<?> clazz, Class<?>... types) {
		if (types == null || types.length == 0)
			throw new IllegalArgumentException("No classes given");
		for (Class<?> t : types){
			boolean check = isOfType(clazz, t);
			if (check)
				return true;
		}
		return false;
	}
}
