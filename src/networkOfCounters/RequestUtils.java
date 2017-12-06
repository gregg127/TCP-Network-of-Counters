package networkOfCounters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Additional class for parsing requests
 * @author Grzesiek
 *
 */
public class RequestUtils { 
	public static final int SINGLE = 1;
	public static final int DOUBLE = 2;
	
	private static Pattern pattern;
	private static Matcher matcher;
	
	/**
	 * Returns request name
	 * @param getRequestLine
	 * @param reqPlurality information whether request is single or double
	 * @return
	 */
	public static String getReqName(String getRequestLine, int reqPlurality) {
		String regex;
		if(reqPlurality == SINGLE) 
			regex = "/\\?(.*)=";
		else if (reqPlurality == DOUBLE) 
			regex = "(.*)=";
		else 
			return null;
		
		try {
			pattern = Pattern.compile(regex);
			matcher = pattern.matcher(getRequestLine);
			if(matcher.find())
				return matcher.group(1);
			else 
				return null;
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Returns value of given request
	 * @param getRequestLine
	 * @param reqPlurality information whether request is single or double
	 * @return
	 */
	public static String getReqValue(String getRequestLine, int reqPlurality) {
		String regex;
		if(reqPlurality == SINGLE) 
			regex = "=(.*) ";
		else if (reqPlurality == DOUBLE) 
			regex = "=(.*) ?";
		else 
			return null;
		
		try {
			pattern = Pattern.compile(regex);
			matcher = pattern.matcher(getRequestLine);
			String res;
			if(matcher.find()) {
				res = matcher.group(1);
				return res.length()==0 ? null : res;
			} else {
				return null;
			}
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			return null;
		}
	}
}