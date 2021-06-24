package za.ac.uct.cs.powerqope.dns;

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


import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Vector;

import za.ac.uct.cs.powerqope.util.ExecutionEnvironment;
import za.ac.uct.cs.powerqope.util.SuppressRepeatingsLogger;

public class DNSFilterProxy implements Runnable {

	private static final String TAG = "DNSFilterProxy";
	DatagramSocket receiver;
	boolean stopped = false;
	int port = 53;

	public DNSFilterProxy(int port) {
		this.port = port;
	}

	private static void initDNS(DNSFilterManager dnsFilterMgr) {
		try {

			boolean detect = Boolean.parseBoolean(dnsFilterMgr.getConfig().getProperty("detectDNS", "true"));
			if (detect) {
				Log.i(TAG,"DNS detection not supported for this device");
				Log.i(TAG,"DNS detection not supported - Using fallback!");
			}
			Vector<DNSServer> dnsAdrs = new Vector<DNSServer>();
			int timeout = Integer.parseInt(dnsFilterMgr.getConfig().getProperty("dnsRequestTimeout", "15000"));

			StringTokenizer fallbackDNS = new StringTokenizer(dnsFilterMgr.getConfig().getProperty("fallbackDNS", ""), ";");
			int cnt = fallbackDNS.countTokens();
			for (int i = 0; i < cnt; i++) {
				String dnsEntry = fallbackDNS.nextToken().trim();
				Log.i(TAG,"DNS:" + dnsEntry);
				try {
					dnsAdrs.add(DNSServer.getInstance().createDNSServer(dnsEntry, timeout));
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				}
			}
			DNSCommunicator.getInstance().setDNSServers(dnsAdrs.toArray(new DNSServer[dnsAdrs.size()]));
        } catch (IOException e) {
            Log.i(TAG,"!!!DNS server initialization failed!!!");
            Log.i(TAG,e.toString());
            Log.i(TAG,e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
	}

	public static void main(String[] args) throws Exception {
		class StandaloneEnvironment extends ExecutionEnvironment  {
			
			boolean debug = false;
			boolean debugInit = false;

			
			@Override
			public boolean debug() {
				if (!debugInit) {
					try {
						debug = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("debug", "false"));
					} catch (IOException e) {
						Log.e(TAG, e.getMessage());
					}
					debugInit=true;
				}
					
				return debug;
			}

			@Override
			public void onReload() {
				DNSFilterProxy.initDNS(DNSFilterManager.getInstance());
			}

			@Override
			public InputStream getAsset(String path) throws IOException {
				return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
			}

		}
		
		ExecutionEnvironment.setEnvironment(new StandaloneEnvironment());
		DNSFilterManager.WORKDIR = ExecutionEnvironment.getEnvironment().getWorkDir();
		
		DNSFilterManager filtermgr = DNSFilterManager.getInstance();
		filtermgr.init();

		long repeatingLogSuppressTime = Long.parseLong(DNSFilterManager.getInstance().getConfig().getProperty("repeatingLogSuppressTime", "1000"));

		initDNS(filtermgr);

		int port = Integer.parseInt(DNSFilterManager.getInstance().getConfig().getProperty("dnsProxyPortNonAndroid","53"));
		DNSFilterProxy runner = new DNSFilterProxy(port);

		runner.run();
	}

	private static InetAddress openVPN4pDNSf_Adr;

	{
		try {
			openVPN4pDNSf_Adr = InetAddress.getByName("10.10.10.10");
		} catch (UnknownHostException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	public static boolean isAlocalAddress(InetAddress addr) throws IOException {

		if (addr.equals(openVPN4pDNSf_Adr)  || addr.isLoopbackAddress() || addr.isAnyLocalAddress())
			return true;

		return NetworkInterface.getByInetAddress(addr) != null;
	}

	@Override
	public void run() {
		int max_resolvers;
		boolean onlyLocal;
		boolean androidRootMode;
		try {
			max_resolvers  = Integer.parseInt(DNSFilterManager.getInstance().getConfig().getProperty("maxResolverCount", "100"));
			onlyLocal = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("dnsProxyOnlyLocalRequests", "true"));
			androidRootMode = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("rootModeOnAndroid", "false"));
		} catch (Exception e) {
			Log.i(TAG,"Exception:Cannot get configuration!");
			Log.e(TAG, e.getMessage());
			return;
		}
		try {
			if (onlyLocal && (ExecutionEnvironment.getEnvironment().getEnvironmentID() == 0 || androidRootMode))
				//currently only possible for non Android - see below!
				receiver = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"));
			else
				receiver = new DatagramSocket(port);

			ExecutionEnvironment.getEnvironment().protectSocket(receiver, 1);

		} catch (IOException eio) {
			Log.i(TAG,"Exception:Cannot open DNS port " + port + "!" + eio.getMessage());
			return;
		}
		Log.i(TAG,"DNSFilterProxy running on port " + port + "!");

		while (!stopped) {
			try {
				byte[] data = new byte[DNSServer.getBufSize()];
				DatagramPacket request = new DatagramPacket(data, 0, DNSServer.getBufSize());
				receiver.receive(request);

				boolean permitted = true;
				// This is temporary solution on Android as here we can not open the localhost socket only
				// due to interoperability with openVPN for pDNSf.
				// Will work on better solution for next release
				if (onlyLocal && ExecutionEnvironment.getEnvironment().getEnvironmentID() == 1 && !androidRootMode)
					permitted = isAlocalAddress(request.getAddress());

				if (!permitted)
					Log.i(TAG,request.getAddress()+" not permitted! Only local access!");

				if (DNSResolver.getResolverCount()>max_resolvers) {
					Log.i(TAG,"Max resolver count reached: "+max_resolvers);
				}
				else if (permitted)
					new Thread(new DNSResolver(request, receiver)).start();

			} catch (IOException e) {
				if (!stopped)
					Log.i(TAG,"Exception:" + e.getMessage());
			}
		}
		Log.i(TAG,"DNSFilterProxy stopped!");
	}


	public synchronized void stop() {
		stopped = true;
		if (receiver == null)
			return;
		receiver.close();
	}


}
