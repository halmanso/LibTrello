package com.openatk.libtrello;

import java.util.Date;
import java.util.List;

public interface ICard extends ICommon {	
	public String getListId();
	public String getBoardId();
	public String getName();
	public String getDesc();
	public List<String> getLabelNames();
	public List<String> getLabels();
	public Date getChangedDate();
}
