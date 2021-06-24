package za.ac.uct.cs.powerqope.util;

public interface ObjectPackagingManager {
	
	public int objectSize();
	
	public  Object bytesToObject(byte[] data, int offs);
	
	public void objectToBytes (Object object, byte[] data, int offs);	
	
}
