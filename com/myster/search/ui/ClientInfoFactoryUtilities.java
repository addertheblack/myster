package com.myster.search.ui;

import com.myster.mml.RobustMML;
import com.general.mclist.*;

public class ClientInfoFactoryUtilities {

	public static ClientHandleObject getHandler(String type) {
		if (type.equals("MPG3")) return new ClientMPG3HandleObject();
		else return new ClientGenericHandleObject();
	}


	
}