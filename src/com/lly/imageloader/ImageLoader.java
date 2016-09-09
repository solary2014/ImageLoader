package com.lly.imageloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import com.lly.imageloader.R;

public class ImageLoader {
	private static final String TAG = "ImageLoader";

	public static final int MESSAGE_POST_RESULT = 1;
	private Context mContext;
	private LruCache<String, Bitmap> mMemoryCache;
	private DiskLruCache mDiskLruCache;
	private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
	private static final int DISK_CACHE_INDEX = 0;
	private static final int IO_BUFFER_SIZE = 8 * 1024;
	private static final int TAG_KEY_URI = R.id.image;
	private boolean mIsDiskLruCacheCreated;
	private static final int CPU_COUNT = Runtime.getRuntime()
            .availableProcessors();
	private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;
	
	 private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
	        @Override
	        public void handleMessage(Message msg) {
	            LoaderResult result = (LoaderResult) msg.obj;
	            ImageView imageView = result.imageView;
	            imageView.setImageBitmap(result.bitmap);
	            String uri = (String) imageView.getTag(TAG_KEY_URI);
	            if (uri.equals(result.uri)) {
	                imageView.setImageBitmap(result.bitmap);
	            } else {
	                Log.w(TAG, "set image bitmap,but url has changed, ignored!");
	            }
	        };
	    };
	    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
	        private final AtomicInteger mCount = new AtomicInteger(1);

	        public Thread newThread(Runnable r) {
	            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
	        }
	    };

	    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
	            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
	            KEEP_ALIVE, TimeUnit.SECONDS,
	            new LinkedBlockingQueue<Runnable>(), sThreadFactory);
	    public static ImageLoader build(Context context) {
	        return new ImageLoader(context);
	    }
	private ImageResizer mImageResizer = new ImageResizer();
	private ImageLoader(Context context){
		mContext = context.getApplicationContext();
		int maxMemory = (int) (Runtime.getRuntime().maxMemory()/1024);
		int cacheSize = maxMemory/8;
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				// TODO Auto-generated method stub
				return bitmap.getRowBytes()*bitmap.getHeight()/1024;
			}
		};
		File diskCacheDir = getDiskCacheDir(mContext,"bitmap");
		if(!diskCacheDir.exists()){
			diskCacheDir.mkdirs();
		}
		if(getUsableSpace(diskCacheDir)>DISK_CACHE_SIZE){
			try {
				mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
				mIsDiskLruCacheCreated = true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	private void addBitmapToMemoryCache(String key,Bitmap bitmap){
		if(getBitmapFromMemCache(key)==null){
			mMemoryCache.put(key, bitmap);
		}
	}
	private Bitmap getBitmapFromMemCache(String key){
		return mMemoryCache.get(key);
	}
	/**
	 * 创建DiskLruCache.Editor 以使创建写入DiskLruCache的输出流文件
	 * @param url
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 * @throws IOException
	 */
	private Bitmap loadBitmapFromHttp(String url,int reqWidth,int reqHeight) throws IOException{
		if(Looper.myLooper() == Looper.getMainLooper()){
			throw new RuntimeException("can not visit network from UI thread.");
		}
		if(mDiskLruCache == null){
			return null;
		}
		String key = hashKeyFormUrl(url);
		DiskLruCache.Editor editor = mDiskLruCache.edit(key);	
		if(editor!=null){
			OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
			if(downloadUrlToStream(url,outputStream)){
				editor.commit();
			}else{
				editor.abort();
			}
			mDiskLruCache.flush();
		}
		return loadBitmapFromDiskCache(url,reqWidth,reqHeight);
	}
	/**
	 * 从DiskLruCache获取图片需要通过文件描述符来缩放图片
	 * @param url
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 * @throws IOException
	 */
	 private Bitmap loadBitmapFromDiskCache(String url, int reqWidth,
	            int reqHeight) throws IOException {
	        if (Looper.myLooper() == Looper.getMainLooper()) {
	            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
	        }
	        if (mDiskLruCache == null) {
	            return null;
	        }

	        Bitmap bitmap = null;
	        String key = hashKeyFormUrl(url);
	        DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
	        if (snapShot != null) {
	            FileInputStream fileInputStream = (FileInputStream)snapShot.getInputStream(DISK_CACHE_INDEX);
	            FileDescriptor fileDescriptor = fileInputStream.getFD();
	            bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor,
	                    reqWidth, reqHeight);
	            if (bitmap != null) {
	                addBitmapToMemoryCache(key, bitmap);
	            }
	        }

	        return bitmap;
	    }
	 /**
	  * 同步加载方式 从内存缓存 或磁盘缓存或网络
	  * @param uri
	  * @param reqWidth
	  * @param reqHeight
	  * @return
	  */
	 public Bitmap loadBitmap(String uri,int reqWidth,int reqHeight){
		 Bitmap bitmap = loadBitmapFromMemCache(uri);
		 if(bitmap!=null){
			 Log.d(TAG,"loadBitmapFromMemCache,url:"+uri);
			 return bitmap;
		 }
		 try {
			bitmap = loadBitmapFromDiskCache(uri, reqWidth, reqHeight);
			if(bitmap!=null){
				Log.d(TAG,"loadBitmapFromDiskCache,url:"+uri);
				return bitmap;
			}
			bitmap = loadBitmapFromHttp(uri, reqWidth, reqHeight);
			Log.d(TAG,"loadBitmapFromHttp,url:"+uri);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 if(bitmap == null && mIsDiskLruCacheCreated){
			 Log.d(TAG,"encounter error DiskLruCache is not Created.");
			 bitmap = downloadBitmapFromUrl(uri);
		 }
		 return bitmap;
		 
	 }
	 /**
	  * 异步加载
	  * @param uri
	  * @param imageView
	  * @param reqWidth
	  * @param reqHeight
	  */
	 public void bindBitmap(final String uri,final ImageView imageView,
			 final int reqWidth,final int reqHeight){
		 imageView.setTag(TAG_KEY_URI,uri);
		 Bitmap bitmap = loadBitmapFromMemCache(uri);
		 if(bitmap!=null){
			 imageView.setImageBitmap(bitmap);
			 return ;
		 }
		 Runnable loadBitmapTask = new Runnable() {
			
			@Override
			public void run() {
				Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);
				if(bitmap!=null){
					LoaderResult result = new LoaderResult(imageView,uri,bitmap);
					mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();	
				}
				
			}
		};
		THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
	 }
	 /**
	  * 从网络下载图片
	  * @param urlString
	  * @return
	  */
	  private Bitmap downloadBitmapFromUrl(String urlString) {
	        Bitmap bitmap = null;
	        HttpURLConnection urlConnection = null;
	        BufferedInputStream in = null;

	        try {
	            final URL url = new URL(urlString);
	            urlConnection = (HttpURLConnection) url.openConnection();
	            in = new BufferedInputStream(urlConnection.getInputStream(),
	                    IO_BUFFER_SIZE);
	            bitmap = BitmapFactory.decodeStream(in);
	        } catch (final IOException e) {
	            Log.e(TAG, "Error in downloadBitmap: " + e);
	        } finally {
	            if (urlConnection != null) {
	                urlConnection.disconnect();
	            }
	            MyUtils.close(in);
	        }
	        return bitmap;
	    }
	 
	  private Bitmap loadBitmapFromMemCache(String url) {
	        final String key = hashKeyFormUrl(url);
	        Bitmap bitmap = getBitmapFromMemCache(key);
	        return bitmap;
	    }
	 /**
	  * 从网络下载图片并写入DiskLruCache缓存
	  * @param urlString
	  * @param outputStream
	  * @return
	  */
	public boolean downloadUrlToStream(String urlString,
            OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            //写入DiskLruCache缓存
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "downloadBitmap failed." + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            MyUtils.close(out);
            MyUtils.close(in);
        }
        return false;
    }
	private String hashKeyFormUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(url.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
	/**
	 * 磁盘剩余的空间
	 * @param path
	 * @return
	 */
	@TargetApi(VERSION_CODES.GINGERBREAD)
	private long getUsableSpace(File path) {
		 if (Build.VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) {
	            return path.getUsableSpace();
	        }
	        final StatFs stats = new StatFs(path.getPath());
	        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
	}

	public File getDiskCacheDir(Context context, String uniqueName) {
        boolean externalStorageAvailable = Environment
                .getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (externalStorageAvailable) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + uniqueName);
    }
	private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }
}
























