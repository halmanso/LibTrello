package edu.purdue.autogenics.libtrello;

import java.util.List;

public interface ICard extends ICommon {	
	public String getListId();
	public String getBoardId();
	public String getName();
	public String getDesc();
	public List<String> getLabelNames();
	public List<String> getLabels();
}
