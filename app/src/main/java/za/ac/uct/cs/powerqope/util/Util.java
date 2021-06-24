/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package za.ac.uct.cs.powerqope.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import za.ac.uct.cs.powerqope.Config;
import za.ac.uct.cs.powerqope.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import za.ac.uct.cs.powerqope.Logger;

/**
 * Utility class for Speedometer that does not require runtime information
 */
public class Util {

  private static final String TAG = "Util";
    
  /**
   * Filter out values equal or beyond the bounds and then compute the average of valid data
   * @param vals the list of values to be filtered
   * @param lowerBound the lower bound of valid data
   * @param upperBound the upper bound of valid data
   * @return a list of filtered data within the specified bounds
   */
  public static ArrayList<Double> applyInnerBandFilter(ArrayList<Double> vals, double lowerBound,
                                                       double upperBound) throws InvalidParameterException {
    
    double rrtTotal = 0;
    int initResultLen = vals.size();
    if (initResultLen == 0) {
      // Return the original array if it is of zero length. 
      throw new InvalidParameterException("The array size passed in is zero");
    }
    
    double rrtAvg = rrtTotal / initResultLen;
    // we should filter out the outliers in the rrt result based on the average
    ArrayList<Double> finalRrtResults = new ArrayList<Double>();
    int finalResultCnt = 0;
    rrtTotal = 0;    
    for (double rrtVal : vals) {
      if (rrtVal <= upperBound && rrtVal >= lowerBound) {
        finalRrtResults.add(rrtVal);
      }
    }

    return finalRrtResults;
  }
  
  /**
   * Compute the sum of the values in list
   * @param vals the list of values to sum up
   * @return the sum of the values in the list
   */
  public static double getSum(ArrayList<Double> vals) {
    double sum = 0;
    for (double val : vals) {
      sum += val;      
    }
    return sum;
  }
  
  public static String constructCommand(Object... strings) throws InvalidParameterException {
    String finalCommand = "";
    int len = strings.length;
    if (len < 0) {
      throw new InvalidParameterException("0 arguments passed in for constructing command");
    }
    
    for (int i = 0; i < len - 1; i++) {
      finalCommand += (strings[i] + " ");
    }
    finalCommand += strings[len - 1];
    return finalCommand;
  }  
  
  /**
   * Prepare the internal User-Agent string for use. This requires a
   * {@link Context} to pull the package name and version number for this
   * application.
   */
  public static String prepareUserAgent(Context context) {
    try {
      // Read package name and version number from manifest
      PackageManager manager = context.getPackageManager();
      PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
      return context.getString(R.string.user_agent);

    } catch (NameNotFoundException e) {
      Logger.e("Couldn't find package information in PackageManager", e);
      return context.getString(R.string.default_user_agent);
    }
  }
  
  public static double getStandardDeviation(ArrayList<Double> values, double avg) {
    double total = 0;
    for (double val : values) {
      double dev = val - avg;
      total += (dev * dev);
    }
    if (total > 0) {
      return Math.sqrt(total / values.size());
    } else {
      return 0;
    }
  }
  
  public static String getTimeStringFromMicrosecond(long microsecond) {
    Date timestamp = new Date(microsecond / 1000);
    return timestamp.toString();
  }

  /**
   * Returns a String array that contains the ICMP sequence number and the round
   * trip time extracted from a ping output. The first array element is the
   * sequence number and the second element is the round trip time.
   * 
   * Returns a null object if either element cannot be found.
   */
  public static String[] extractInfoFromPingOutput(String outputLine) {
    try {
      Pattern pattern = Pattern.compile("icmp_seq=([0-9]+)\\s.* time=([0-9]+(\\.[0-9]+)?)");
      Matcher matcher = pattern.matcher(outputLine);
      matcher.find();
      
      return new String[] {matcher.group(1), matcher.group(2)};
    } catch (IllegalStateException e) {
      return null;
    }
  }
  
  /**
   * Returns an integer array that contains the number of ICMP requests sent and the number
   * of responses received. The first element is the requests sent and the second element
   * is the responses received.
   * 
   * Returns a null object if either element cannot be found.
   */
  public static int[] extractPacketLossInfoFromPingOutput(String outputLine) {
    try {
      Pattern pattern = Pattern.compile("([0-9]+)\\spackets.*\\s([0-9]+)\\sreceived");
      Matcher matcher = pattern.matcher(outputLine);
      matcher.find();
      
      return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    } catch (IllegalStateException e) {
      return null;
    } catch (NumberFormatException e) {
      return null;
    } catch (NullPointerException e) {
      return null;
    }
  }
  
  /**
   * Return a list of system environment path 
   */
  public static String[] fetchEnvPaths() {
    String path = "";
    Map<String, String> env = System.getenv();
    if (env.containsKey("PATH")) {
      path = env.get("PATH");
    }
    return (path.contains(":")) ? path.split(":") : (new String[]{path});
  }

  /**
   * Determine the ping executable based on ip address byte length
   */
  public static String pingExecutableBasedOnIPType (int ipByteLen, Context context) {
    Process testPingProc = null;
    String[] progList = fetchEnvPaths();
    String pingExecutable = null;
    if (progList != null && progList.length != 0) {
      for (String pingLocation : progList) {
        try {
          if (ipByteLen == 4) {
            pingExecutable = pingLocation + "/" + 
                             context.getString(R.string.ping_executable);
          } else if (ipByteLen == 16) {
            pingExecutable = pingLocation + "/" + 
                             context.getString(R.string.ping6_executable);
          }
          testPingProc = Runtime.getRuntime().exec(pingExecutable);
        } catch (IOException e) {
          // reset the executable
          pingExecutable = null;
          // The ping command doesn't exist in that path, try another one
          continue;
        } finally {
          if (testPingProc != null)
            testPingProc.destroy();
        }
        break;
      }
    }
    return pingExecutable;
  }

  public static String resolveServer(){
    try {
      InetAddress inetAddress = InetAddress.getByName(Config.SERVER_HOST_ADDRESS);
      return inetAddress.getHostAddress();
    }
    catch (UnknownHostException e){
      e.printStackTrace();
    }
    return null;
  }

  public static String getWebSocketTarget() {
    String serverIP = resolveServer();
    Log.i(TAG, "getWebSocketTarget: "+serverIP);
    return "ws://" + serverIP + ":" + Config.SERVER_PORT + Config.STOMP_SERVER_CONNECT_ENDPOINT;
  }

  public static String hashTimeStamp() {
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      return "";
    }
    String timestamp = new Date().toString();
    byte[] hashInBytes = md.digest(timestamp.getBytes(StandardCharsets.UTF_8));

    StringBuilder sb = new StringBuilder();
    for (byte b : hashInBytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  public static int byteArrayToInt(byte[] b) {
    return b[3] & 0xFF |
            (b[2] & 0xFF) << 8 |
            (b[1] & 0xFF) << 16 |
            (b[0] & 0xFF) << 24;
  }

  public static byte[] intToByteArray(int a) {
    return new byte[]{
            (byte) ((a >> 24) & 0xFF),
            (byte) ((a >> 16) & 0xFF),
            (byte) ((a >> 8) & 0xFF),
            (byte) (a & 0xFF)
    };
  }

  public static byte[] longToByteArray(long value) {
    return new byte[]{
            (byte) (value >> 56),
            (byte) (value >> 48),
            (byte) (value >> 40),
            (byte) (value >> 32),
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value
    };
  }

  public static void writeLongToByteArray(long value, byte[] b, int offs) {
    b[offs + 0] = (byte) (value >> 56);
    b[offs + 1] = (byte) (value >> 48);
    b[offs + 2] = (byte) (value >> 40);
    b[offs + 3] = (byte) (value >> 32);
    b[offs + 4] = (byte) (value >> 24);
    b[offs + 5] = (byte) (value >> 16);
    b[offs + 6] = (byte) (value >> 8);
    b[offs + 7] = (byte) value;
  }

  public static long byteArrayToLong(byte[] b, int offs) {
    return (long) (b[7 + offs] & 0xFF) |
            (long) (b[6 + offs] & 0xFF) << 8 |
            (long) (b[5 + offs] & 0xFF) << 16 |
            (long) (b[4 + offs] & 0xFF) << 24 |
            (long) (b[3 + offs] & 0xFF) << 32 |
            (long) (b[2 + offs] & 0xFF) << 40 |
            (long) (b[1 + offs] & 0xFF) << 48 |
            (long) (b[0 + offs] & 0xFF) << 56;
  }


  public static long getLongStringHash(String str) {
    int a = 0;
    int b = 0;
    int len = str.length();
    byte[] bytes = str.getBytes();
    for (int i = 0; i < len; i++) {
      a = 31 * a + (bytes[i] & 0xFF);
      b = 31 * b + (bytes[len - i - 1] & 0xFF);
    }

    return ((long) a << 32) | ((long) b & 0xFFFFFFFFL);
  }

  public static boolean arrayEqual(Object[] a1, Object[]a2){
    if (a1.length != a2.length)
      return false;

    for (int i = 0; i < a1.length; i++)
      if (!a1[i].equals(a2[i]))
        return false;

    return true;
  }

  public static void closeSocket(Socket s){
    try {
      s.shutdownOutput();
    } catch (IOException e) {
      //Logger.getLogger().logLine("Exception during closeConnection(): " + e.toString());
    }
    try {
      s.shutdownInput();
    } catch (IOException e) {
      //Logger.getLogger().logLine("Exception during closeConnection(): " + e.toString());
    }
    try {
      s.close();
    } catch (IOException e) {
      //Logger.getLogger().logLine("Exception during closeConnection(): " + e.toString());
    }
  }

  public static String readLineFromStream(InputStream in, boolean crlf) throws IOException {

    int i = 0;
    StringBuffer str = new StringBuffer();
    boolean exit = false;
    int r = -1;
    byte last = 0;
    while (!exit) {
      r = in.read();
      byte b = (byte) (r);
      exit = (r == -1 || (b == 10 && (!crlf || last == 13)));
      if (!exit) {
        str.append((char) b);
        i++;
        last = b;
      }
    }

    if (r == -1 && i == 0)
      throw new EOFException("Stream is closed!");

    if (i > 0 && last == 13)
      i = i - 1;

    return str.substring(0, i);
  }

  public static int readLineBytesFromStream(InputStream in, byte[] buf, boolean printableOnly, boolean ignoreComment) throws IOException {

    int r = in.read();
    while (ignoreComment && r == 35) {
      //lines starts with # - ignore line!
      r = skipLine(in);

      if (r != -1)
        r = in.read();
    }

    if (r == -1)
      return -1;

    if (buf.length == 0)
      throw new IOException("Buffer Overflow!");

    buf[0] = (byte)r;
    int pos = 1;

    while (r != -1 && r!=10) {

      while (r != -1 && r!=10) {

        r = in.read();

        if (r != -1) {
          if (pos == buf.length)
            throw new IOException("Buffer overflow!");

          if (printableOnly && r < 32 && r < 9 && r > 13)
            throw new IOException ("Non printable character: "+r+"("+((char)r)+")");

          buf[pos] = (byte) (r);
          pos++;
        }
      }
    }
    return pos;
  }


  public static int skipLine(InputStream in) throws IOException {
    int r = 0;
    while (r != -1 && r != 10)
      r = in.read();

    return r;
  }

  public static int skipWhitespace(InputStream in, int r) throws IOException {
    while (r != -1 && r != 10 && (r == 9 || r == 32 || r == 13) )
      r = in.read();

    return r;
  }

  public static String readLineFromStreamRN(InputStream in) throws IOException {
    return readLineFromStream(in, true);
  }

  public static String readLineFromStream(InputStream in) throws IOException {
    return readLineFromStream(in, false);
  }


  public static byte[] readFully(InputStream in, int bufSize) throws IOException {
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    int r = 0;
    byte[] buf = new byte[bufSize];
    while ((r = in.read(buf, 0, bufSize)) != -1)
      bytesOut.write(buf, 0, r);
    return bytesOut.toByteArray();

  }

  public static byte[] serializeObject(Object obj) throws IOException {
    ByteArrayOutputStream objOut = new ByteArrayOutputStream();
    ObjectOutputStream dataOut = new ObjectOutputStream(objOut);
    dataOut.writeObject(obj);
    dataOut.flush();
    dataOut.close();
    return objOut.toByteArray();
  }

  public static Object deserializeObject(byte[] bytes) throws IOException {
    ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
    ObjectInputStream objIn = new ObjectInputStream (bytesIn);
    try {
      return objIn.readObject();
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  public static String getServerTime() {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return dateFormat.format(calendar.getTime());
  }


  public static String[] parseURI(String uri) throws IOException {
    try {
      String url = uri;
      url = url.substring(7);
      int idx = url.indexOf('/');
      if (idx == -1)
        idx = url.length();
      String hostEntry = url.substring(0, idx);
      if (idx == url.length())
        url = "/";
      else
        url = url.substring(idx);

      return new String[]{hostEntry, url};
    } catch (Exception e) {
      throw new IOException("Cannot parse URI '" + uri + "'! - " + e.toString());
    }
  }

  public static void deleteFolder(String path) {
    File dir = new File(path);
    if (dir.exists() && dir.isDirectory()) {

      File[] files = dir.listFiles();
      for (int i = 0; i < files.length; i++) {
        if (files[i].isDirectory())
          deleteFolder(files[i].getAbsolutePath());
        else
          files[i].delete();
      }
      dir.delete();
    }
  }

  public static void copyFully(InputStream in, OutputStream out, boolean close) throws IOException {
    byte[] buf = new byte[1024];
    int r = 0;

    while ((r = in.read(buf)) != -1)
      out.write(buf, 0, r);

    out.flush();
    if (close) {
      out.close();
      in.close();
    }
  }

  public static void copyFile(File from, File to) throws IOException {
    File dir = to.getParentFile();
    if (dir != null)
      dir.mkdirs();
    InputStream in = new BufferedInputStream(new FileInputStream(from));
    OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
    copyFully(in, out, true);
  }

  public static void moveFileTree(File sourceFile, File destFile) throws IOException {

    if (sourceFile.isDirectory()) {
      for (File file : sourceFile.listFiles()) {
        moveFileTree(file, new File(file.getPath().replace(sourceFile.getPath(), destFile.getPath())));
        sourceFile.delete();
      }
    } else {
      copyFile(sourceFile, destFile);
      sourceFile.delete();
    }

  }

}
