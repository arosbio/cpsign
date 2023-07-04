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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Named;

public class TypeUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(TypeUtils.class);
	
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

	/**
	 * Converts an object into an int if possible. Although not strictly mathematically correct, Strings of {@code Inf}/{@code Infinity}
	 * will be converted into Integer.MAX_VALUE and {@code -Inf}/{@code -Infinity} to Integer.MIN_VALUE, which for most cases will 
	 * be the intent for the purpose of the user. 
	 * @param obj to be converted
	 * @return value converted to int
	 * @throws NumberFormatException If {@code obj} could not be converted
	 */
	public static int asInt(Object obj) throws NumberFormatException {
		if (obj instanceof Integer) {
			return (int) obj;
		} else if (obj instanceof Long) {
			return ((Long) obj).intValue();
		} else if (obj instanceof String) {
			try {
				Long asLong = Long.parseLong((String)obj);
				return asLong.intValue();
			} catch (NumberFormatException e) {
				LOGGER.debug("Failed parsing input '{}' into an int - checking if it matches NaN, Inf, -Inf etc",obj);
				try {
					String txt = ((String)obj).trim();
					if ("NaN".equalsIgnoreCase(txt)){
						LOGGER.debug("Value was NaN");
					} 
					boolean isNegative = txt.startsWith("-");
					boolean hasPlusSign = txt.startsWith("+");
					if (hasPlusSign || isNegative){
						// read the + or - sign
						txt = txt.substring(1);
					}
					if (txt.equalsIgnoreCase("Inf") || txt.equalsIgnoreCase("Infinity")){
						return isNegative ? Integer.MIN_VALUE : Integer.MAX_VALUE;
					}
				} catch (Exception e2){}
			}
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

	/**
	 * Convert object into a double value if possible. 
	 * @param obj to be converted
	 * @return the value converted to double 
	 * @throws NumberFormatException If {@code obj} could not be converted
	 */
	public static double asDouble(Object obj) throws NumberFormatException {
		if (obj instanceof Double){
			return ((Double) obj).doubleValue();
		} else if (obj instanceof BigDecimal) {
			return ((BigDecimal) obj).doubleValue();
		} else if (obj instanceof Number) {
			return ((Number) obj).doubleValue();
		} else if (obj instanceof String) {
			try {
				return Double.parseDouble((String)obj);
			} catch (NumberFormatException e) {
				LOGGER.debug("Failed parsing input '{}' into a double - checking if it matches NaN, Inf, -Inf etc",obj);
				try {
					String txt = ((String)obj).trim();
					if ("NaN".equalsIgnoreCase(txt)){
						LOGGER.debug("Value was NaN");
						return Double.NaN;
					} 
					boolean isNegative = txt.startsWith("-");
					boolean hasPlusSign = txt.startsWith("+");
					if (hasPlusSign || isNegative){
						// read the + or - sign
						txt = txt.substring(1);
					}
					if (txt.equalsIgnoreCase("Inf") || txt.equalsIgnoreCase("Infinity")){
						return isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
					}
				} catch (Exception e2){}
			}
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

		if (lowerCase.equals("t")|| lowerCase.equals("true") || lowerCase.equals("y") || lowerCase.equals("yes")) {
			return true;
		} else if (lowerCase.equals("f") || lowerCase.equals("false") || lowerCase.equals("n") || lowerCase.equals("no")) {
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
