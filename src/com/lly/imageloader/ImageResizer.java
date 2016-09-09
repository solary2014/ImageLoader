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
	 * 通过资源文件进行图片压缩
	 * @param res
	 * @param resId
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public Bitmap decodeSampledBitmapFromResource(Resources res,
			int resId,int reqWidth,int reqHeight){
		//1、将BitmapFactory.Options的inJustDecodeBounds参数设为true并加载图片。
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, options);
		//2、从BitmapFactory.Options中取出图片的原始宽高信息，他们对应于outWidth和outHeight参数。
		options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
				
		//4、将BitmapFactory.Options的inJustDecodeBounds参数设为false，然后重新加载图片。
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeResource(res, resId, options);
	}
	/**
	 * 通过文件进行图片压缩
	 * @param fd
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd,
			int reqWidth,int reqHeight){
		//1、将BitmapFactory.Options的inJustDecodeBounds参数设为true并加载图片。
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFileDescriptor(fd, null, options);
		//2、从BitmapFactory.Options中取出图片的原始宽高信息，他们对应于outWidth和outHeight参数。
		options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
		//4、将BitmapFactory.Options的inJustDecodeBounds参数设为false，然后重新加载图片。
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
			//3、根据采样率的规则并结合目标View的所需大小计算出采样率inSampleSize。
			while((halfHeight/inSampleSize>=reqheight)&&(halfWidth/inSampleSize>=reqwidth)){
				inSampleSize*=2;
			}
		}
		return inSampleSize;
	}
}























