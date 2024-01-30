package io.github.imaron85.wakamine.wakatime;

import java.math.BigDecimal;

public class Heartbeat {
	public String entity;
	public Integer lineCount;
	public Integer lineNumber;
	public Integer cursorPosition;
	public BigDecimal timestamp;
	public Boolean isWrite;
	public Boolean isUnsavedFile;
	public String project;
	public String language;
	public Boolean isBuilding;
}
