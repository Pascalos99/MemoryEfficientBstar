package algorithm;

import java.util.Locale;

/**
 * A simple debug class.
 */
public class DebugPrinter {

	private long debugStart, debugEnd, debug;
	private boolean longDebug;
	private Locale standard;
	
	public DebugPrinter(Locale locale) {
		debugStart = 0;
		debugEnd = 0;
		debug = 0;
		longDebug = false;
		standard = locale;
	}
	public DebugPrinter() {
		this(Locale.CANADA);
	}
	
	public void setLocale(Locale locale) {
		standard = locale;
	}
	
	public void setDebug(long startAt, long endAt) {
		debugStart = startAt;
		debugEnd = endAt;
	}
	public void setDebug(boolean set) {
		if (set) setDebug(0, Long.MAX_VALUE);
		else setDebug(0,0);
	}
	public void setDebugLong(boolean set) {
		longDebug = set;
	}
	
	public void reset() {
		debug = 0;
	}
	
	public void debug(boolean showIndex, String msg) {
		if (debug >= debugStart && debug < debugEnd)
			System.out.format("%s%s", showIndex ? String.format("%05d: ", debug) : "", msg);
		if (showIndex) debug++;
	}
	public void debug(boolean showIndex, String msg_to_format, Object... objs) {
		if (objs.length > 0)
			debug(showIndex, String.format(standard, msg_to_format, objs));
		else
			debug(showIndex, msg_to_format);
	}
	public void debug(boolean showIndex, Locale loc, String msg_to_format, Object... objs) {
		if (objs.length > 0)
			debug(showIndex, String.format(loc, msg_to_format, objs));
		else
			debug(showIndex, msg_to_format);
	}
	public void debug(String msg) {
		debug(true, msg);
	}
	public void debug(String msg_to_format, Object... objs) {
		debug(true, msg_to_format, objs);
	}
	public void debug(Locale loc, String msg_to_format, Object... objs) {
		debug(true, loc, msg_to_format, objs);
	}
	
	public void debugL(boolean showIndex, String msg) {
		if (longDebug) debug(showIndex, msg);
	}
	public void debugL(boolean showIndex, String msg_to_format, Object... objs) {
		if (longDebug) debug(showIndex, msg_to_format, objs);
	}
	public void debugL(boolean showIndex, Locale loc, String msg_to_format, Object... objs) {
		if (longDebug) debug(showIndex, loc, msg_to_format, objs);
	}
	public void debugL(String msg) {
		debugL(true, msg);
	}
	public void debugL(String msg_to_format, Object... objs) {
		debugL(true, msg_to_format, objs);
	}
	public void debugL(Locale loc, String msg_to_format, Object... objs) {
		debugL(true, loc, msg_to_format, objs);
	}
	
	public void debugS(boolean showIndex, String msg) {
		if (!longDebug) debug(showIndex, msg);
	}
	public void debugS(boolean showIndex, String msg_to_format, Object... objs) {
		if (!longDebug) debug(showIndex, msg_to_format, objs);
	}
	public void debugS(boolean showIndex, Locale loc, String msg_to_format, Object... objs) {
		if (!longDebug) debug(showIndex, loc, msg_to_format, objs);
	}
	public void debugS(String msg) {
		debugS(true, msg);
	}
	public void debugS(String msg_to_format, Object... objs) {
		debugS(true, msg_to_format, objs);
	}
	public void debugS(Locale loc, String msg_to_format, Object... objs) {
		debugS(true, loc, msg_to_format, objs);
	}
	
}
