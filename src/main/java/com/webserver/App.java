package com.webserver;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class App {
	/**
	 * Says hello to the world via web.
	 * @param args The arguments of the program.
	 * @throws UnknownHostException
	 */
	public static void main(String[] args) throws UnknownHostException {
		Server server = new Server();
		server.start(InetAddress.getByName("127.0.0.1"), 3000, 10, "C:\\pruebas");
		// server.stop();
	}
}
