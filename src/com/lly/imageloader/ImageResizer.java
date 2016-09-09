package com.lly.imageloader;

import java.io.FileDescriptor;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageResizer {

	private static final String TAG = "ImageResizer";
	
	public ImageResizer(){
		
	}
	/**
	 * ͨ����Դ�ļ�����ͼƬѹ��
	 * @param res
	 * @param resId
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public Bitmap decodeSampledBitmapFromResource(Resources res,
			int resId,int reqWidth,int reqHeight){
		//1����BitmapFactory.Options��inJustDecodeBounds������Ϊtrue������ͼƬ��
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, options);
		//2����BitmapFactory.Options��ȡ��ͼƬ��ԭʼ�����Ϣ�����Ƕ�Ӧ��outWidth��outHeight������
		options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
				
		//4����BitmapFactory.Options��inJustDecodeBounds������Ϊfalse��Ȼ�����¼���ͼƬ��
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeResource(res, resId, options);
	}
	/**
	 * ͨ���ļ�����ͼƬѹ��
	 * @param fd
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd,
			int reqWidth,int reqHeight){
		//1����BitmapFactory.Options��inJustDecodeBounds������Ϊtrue������ͼƬ��
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFileDescriptor(fd, null, options);
		//2����BitmapFactory.Options��ȡ��ͼƬ��ԭʼ�����Ϣ�����Ƕ�Ӧ��outWidth��outHeight������
		options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
		//4����BitmapFactory.Options��inJustDecodeBounds������Ϊfalse��Ȼ�����¼���ͼƬ��
		options.inJustDecodeBounds = false;	
		return BitmapFactory.decodeFileDescriptor(fd, null, options);
	}
	public int calculateInSampleSize(BitmapFactory.Options options, int reqwidth,
			int reqheight) {
		if(reqwidth ==0||reqheight == 0){
			return 1;
		}
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize =1;
		if(height>reqheight || width>reqwidth){
			final int halfHeight = height/2;
			final int halfWidth = width/2;
			//3�����ݲ����ʵĹ��򲢽��Ŀ��View�������С�����������inSampleSize��
			while((halfHeight/inSampleSize>=reqheight)&&(halfWidth/inSampleSize>=reqwidth)){
				inSampleSize*=2;
			}
		}
		return inSampleSize;
	}
}























