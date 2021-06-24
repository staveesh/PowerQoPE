/* 
 PersonalDNSFilter 1.5
 Copyright (C) 2017 Ingo Zenz

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

 Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
 Contact:i.z@gmx.net 
 */
package za.ac.uct.cs.powerqope.dns;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Date;

import za.ac.uct.cs.powerqope.util.ExecutionEnvironment;
import za.ac.uct.cs.powerqope.util.Util;

public class DNSCommunicator {
	private static final String TAG = "DNSCommunicator";
	private static DNSCommunicator INSTANCE = new DNSCommunicator();

	private static int TIMEOUT = 12000;
	DNSServer[] dnsServers = new DNSServer[0];
	DNSServer[] currentCheckingDNServers;
	int curDNS = -1;
	String lastDNS = "";


	public static DNSCommunicator getInstance() {
		return INSTANCE;
	}

	public synchronized void setDNSServers(DNSServer[] newDNSServers) throws IOException {

		if (newDNSServers.length > 20)
			throw new IOException ("Too many DNS servers configured - Add max 20!");

		dnsServers = newDNSServers;
		if (dnsServers.length > 0) {
			curDNS = 0;
			lastDNS = dnsServers[curDNS].toString();
			setFastestDNSFromServers(true);
		} else {
			lastDNS = "";
			curDNS = -1;
		}
		if (ExecutionEnvironment.getEnvironment().debug())
			Log.i(TAG,"Using updated DNS servers!");
	}

	private void setFastestDNSFromServers(final boolean acceptCurrent)  {
		final DNSServer[] dnsServersCopy;
		final int[] curDNSCopy = new int[1];
		final FileOutputStream[] dnsPerfOut = new FileOutputStream[1];

		synchronized (INSTANCE) {
			if (dnsServers.length == 1) {
				curDNS = 0;
				return; //no alternative!
			}
			try {

				if (currentCheckingDNServers != null && Util.arrayEqual(currentCheckingDNServers, this.dnsServers))
					return; // already triggered!

				File dnsPerfFile= new File(ExecutionEnvironment.getEnvironment().getWorkDir()+"dnsperf.info");
				if (!dnsPerfFile.exists() || dnsPerfFile.delete()) {
					dnsPerfOut[0] = new FileOutputStream(dnsPerfFile);
					dnsPerfOut[0].write(("#DNS Response Times\r\n#Started: " + new Date() + "\r\n\r\n").getBytes());
				} else
					throw new IOException("Current file cannot be overwritten!");
			} catch (IOException eio) {
				Log.i(TAG,"Can't create dnsperf.info file!\n"+eio);
			}
			curDNSCopy[0] = curDNS;
			dnsServersCopy = new DNSServer[this.dnsServers.length];
			for (int i = 0; i < this.dnsServers.length; i++)
				dnsServersCopy[i]= this.dnsServers[i];

			currentCheckingDNServers = dnsServersCopy;
		}


		new Thread(new Runnable() {

			boolean fastestFound = false;
			boolean allReady = false;
			boolean go = false;
			Object monitor = new Object();
			int cnt = 0;


			public void terminated(boolean first) {
				synchronized (monitor) {
					cnt++;
					if (cnt == dnsServersCopy.length)
						allReady = true;
					if (first || allReady) {
						fastestFound = true;
						monitor.notifyAll();
					}
				}
			}

			@Override
			public void run() {

				//Prepare Test Thread per DNS Server

				for (int i = 0; i < dnsServersCopy.length; i++) {
					final int finalI = i;
					new Thread(new Runnable() {
						DNSServer dnsServer = dnsServersCopy[finalI];
						int dnsIdx = finalI;

						public void writeDNSPerfInfo(String txt) {
							try {
								if (dnsPerfOut[0]!=null)
									dnsPerfOut[0].write(txt.getBytes());
							} catch (IOException eio) {
								Log.i(TAG,"Can't write dnsperf.info file!\n"+eio);
							}
						}

						@Override
						public void run() {
							synchronized (monitor) {
								while (!go) {
									try {
										monitor.wait();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							}
							try {
								long result = dnsServer.testDNS(5);

								synchronized (monitor) {
									//Log.i(TAG,dnsServer+": "+result+"ms");
									writeDNSPerfInfo(dnsServer+": "+result+"ms\r\n");

									if (!fastestFound) {
										if (acceptCurrent || dnsIdx != curDNSCopy[0]) {
											curDNSCopy[0] = dnsIdx;
											terminated(true);
										} else {
											Log.i(TAG,dnsServer+" already set! Preferring different one!");
											terminated(false);
										}
									} else terminated(false);
								}
							} catch (IOException eio) {
								//Log.i(TAG,dnsServer+": "+eio.getMessage());
								writeDNSPerfInfo(dnsServer+"; "+eio.getMessage()+"\r\n");
								terminated(false);
							}
						}
					}).start();
				}


				synchronized (monitor){

					// Trigger the Test Threads to start testing

					go = true;
					monitor.notifyAll();

					// Wait for the first successfull Thread to finish

					while (!fastestFound) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Take over result in case no other DNS Serverlist change was in between
					synchronized (INSTANCE) {

						if (Util.arrayEqual(dnsServersCopy, dnsServers)) {

							currentCheckingDNServers = null;

							curDNS = curDNSCopy[0];

							if (curDNS != -1) {
								Log.i(TAG,"Selected DNS: (" + dnsServersCopy[curDNS].lastPerformance + "ms) " + dnsServersCopy[curDNS]);
								lastDNS = dnsServers[curDNS].toString();
							}
						}
					}

					// Wait for all Threads to finish
					while (!allReady) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					//close dnsperf.info file

					if (dnsPerfOut[0] != null) {
						try {
							dnsPerfOut[0].write(("\r\n#Terminated: " + new Date() + "\r\n\r\n").getBytes());
							dnsPerfOut[0].flush();
							dnsPerfOut[0].close();
						} catch (IOException eio) {
							Log.i(TAG,"Can't close dnsperf.info file!\n"+eio);
						}
					}
				}
			}
		}).start();
	}

	private synchronized void switchDNSServer(DNSServer current) throws IOException {
		if (current == getCurrentDNS()) {  //might have been switched by other thread already
			//curDNS = (curDNS + 1) % dnsServers.length;
			setFastestDNSFromServers(false);
			if (ExecutionEnvironment.getEnvironment().debug())
				Log.i(TAG,"Switched DNS server to:" + getCurrentDNS().getAddress().getHostAddress());
		}
	}

	public synchronized DNSServer getCurrentDNS() throws IOException {
		if (dnsServers.length == 0)
			throw new IOException("No DNS server initialized!");
		else {
			lastDNS = dnsServers[curDNS].toString();
			return dnsServers[curDNS];
		}
	}

	public String getLastDNSAddress() {
		return lastDNS;
	}

	public void requestDNS(DatagramPacket request, DatagramPacket response) throws IOException {

		DNSServer dns = getCurrentDNS();

		try {
			//DNSServer.getInstance().createDNSServer(DNSServer.UDP,dns,53,TIMEOUT, null).resolve(request, response);
			dns.resolve(request, response);
		} catch (IOException eio) {
			if (ExecutionEnvironment.getEnvironment().hasNetwork())
				switchDNSServer(dns);
			//Logger.getLogger().logException(eio);
			throw eio;
		}

	}
}
