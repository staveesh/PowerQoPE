package za.ac.uct.cs.powerqope.util;

import java.util.HashMap;
import java.util.Map;

public class SuppressRepeatingsLogger implements LoggerInterface {
	LoggerInterface nestedLogger;
	private String lastLog = "";
	private HashMap<String, Long> lastLogs = new HashMap<String, Long>();
	private long timeRepeat = 0;
	private long lastCleanup = 0;


	public SuppressRepeatingsLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	public void setNestedLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	public LoggerInterface getNestedLogger() {
		return nestedLogger;
	}

	public void setSuppressTime(long suppressTime) {
		timeRepeat = suppressTime;
	}

	private boolean repeatingLog(String logStr){

		long lastLogged = 0;

		synchronized (lastLogs) {
			long current = System.currentTimeMillis();

			//check when last Logged
			Long entry = lastLogs.get(logStr);
			if (entry != null)
				lastLogged = entry.longValue();

			//update lastLogged
			lastLogs.put(logStr, current);

			//trigger cleanup of old logs in case not done in last time frame
			if (current - lastCleanup > timeRepeat) {
				Map.Entry<String, Long>[] entries = lastLogs.entrySet().toArray(new Map.Entry[lastLogs.size()]);
				for (int i = 0; i < entries.length; i++){
					if (current - entries[i].getValue().longValue() > timeRepeat)
						lastLogs.remove(entries[i].getKey());
				}
				lastCleanup = current;
			}

			return (current-lastLogged <= timeRepeat);
		}
	}

	@Override
	public void logLine(String txt) {
		if (!repeatingLog(txt))
			nestedLogger.logLine(txt);
		lastLog = txt;
	}

	@Override
	public void logException(Exception e) {
		nestedLogger.logException(e);
	}

	@Override
	public void log(String txt) {
		if (!repeatingLog(txt))
			nestedLogger.log(txt);
		lastLog = txt;
	}

	@Override
	public void message(String txt) {
			nestedLogger.message(txt);
	}

	@Override
	public void closeLogger() {
		lastLogs.clear();
		nestedLogger.closeLogger();
	}


}
