package com.xunua.webrtcdemo.utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.WindowManager;



/**
 * TODO<屏幕分辨率工具类> 
 */
public class ScreenUtil {


	/**
	 * 获取手机屏幕的宽度
	 */
	public static int getScreenWidth(Context context) {
		//安卓4.2以上提供的方法，判断绝对值的屏幕宽度（忽略导航栏）
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getRealMetrics(dm);
		return dm.widthPixels;
	}

	/**
	 * 获取手机屏幕的高度
	 */
	public static int getScreenHeight(Context context) {
		//安卓4.2以上提供的方法，判断绝对值的屏幕高度（忽略导航栏）
		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getRealMetrics(dm);
		return dm.heightPixels;
	}

//	/**
//	 * 根据手机分辨率将dp转为px单位
//	 */
//	public static int dip2px(float dpValue) {
//		final float scale = PaperlessApplication.getmContext().getResources()
//				.getDisplayMetrics().density;
//		return (int) (dpValue * scale + 0.5f);
//	}
//
//	/**
//	 * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
//	 */
//	public static int px2dip(float pxValue) {
//		final float scale = PaperlessApplication.getmContext().getResources()
//				.getDisplayMetrics().density;
//		return (int) (pxValue / scale + 0.5f);
//	}
	/**
	 * convert px to its equivalent sp
	 *
	 * 将px转换为sp
	 */
	public static int px2sp(Context context, float pxValue) {
		final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (pxValue / fontScale + 0.5f);
	}


	/**
	 * convert sp to its equivalent px
	 *
	 * 将sp转换为px
	 */
	public static int sp2px(Context context, float spValue) {
		final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (spValue * fontScale + 0.5f);
	}
}