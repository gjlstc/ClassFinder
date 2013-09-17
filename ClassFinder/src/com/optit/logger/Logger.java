package com.optit.logger;

public interface Logger
{
	public void log();
	
	public void log(String line);
	
	public void log(String className, String location);
	
	public void log(String className, String location, String Method);
	
	public void logErr(String line);
	
	public void logVerbose(String line);
	
	public void setVerbose(boolean verbose);
	
	public boolean getVerbose();
}
