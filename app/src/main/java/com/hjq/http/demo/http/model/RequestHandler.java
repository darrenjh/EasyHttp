package com.hjq.http.demo.http.model;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.$Gson$Types;
import com.hjq.http.EasyLog;
import com.hjq.http.config.IRequestHandler;
import com.hjq.http.demo.R;
import com.hjq.http.exception.CancelException;
import com.hjq.http.exception.DataException;
import com.hjq.http.exception.HttpException;
import com.hjq.http.exception.NetworkException;
import com.hjq.http.exception.ResponseException;
import com.hjq.http.exception.ResultException;
import com.hjq.http.exception.ServerException;
import com.hjq.http.exception.TimeoutException;
import com.hjq.http.exception.TokenException;
import com.hjq.http.listener.OnHttpListener;
import com.hjq.toast.ToastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/EasyHttp
 *    time   : 2019/05/19
 *    desc   : 请求处理类
 */
public final class RequestHandler implements IRequestHandler {

    private static final Gson GSON = new Gson();

    private HashMap<Call, ProgressDialog> mDialogs = new HashMap<>();

    @Override
    public void requestStart(Context context, final Call call) {
        if (context == null) {
            return;
        }

        // 显示进度对话框
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage(context.getString(R.string.http_loading));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (call != null) {
                    call.cancel();
                }
            }
        });
        mDialogs.put(call, dialog);
        dialog.show();
    }

    @Override
    public void requestEnd(Context context, Call call) {
        ProgressDialog dialog = mDialogs.get(call);
        if (dialog != null) {
            dialog.dismiss();
            mDialogs.remove(call);
        }
    }

    @Override
    public Object requestSucceed(Context context, Response response, Class clazz) throws Exception {
        if (!response.isSuccessful()) {
            // 返回响应异常
            throw new ResponseException(context.getString(R.string.http_server_error), response);
        }

        ResponseBody body = response.body();
        if (body == null) {
            return null;
        }

        if (Response.class.equals(clazz)) {
            return response;
        }

        if (Bitmap.class.equals(clazz)) {
            // 如果这是一个 Bitmap 对象
            return BitmapFactory.decodeStream(body.byteStream());
        }

        String text;
        try {
            text = body.string();
        } catch (IOException e) {
            // 返回结果读取异常
            throw new DataException(context.getString(R.string.http_data_explain_error), e);
        }

        // 打印这个 Json
        EasyLog.json(text);

        final Object result;
        if (String.class.equals(clazz)) {
            // 如果这是一个 String 对象
            result = text;
        } else if (JSONObject.class.equals(clazz)) {
            try {
                // 如果这是一个 JSONObject 对象
                result = new JSONObject(text);
            } catch (JSONException e) {
                throw new DataException(context.getString(R.string.http_data_explain_error), e);
            }
        } else if (JSONArray.class.equals(clazz)) {
            try {
                // 如果这是一个 JSONArray 对象
                result = new JSONArray(text);
            }catch (JSONException e) {
                throw new DataException(context.getString(R.string.http_data_explain_error), e);
            }
        } else {

            try {
                result = GSON.fromJson(text, clazz);
            } catch (JsonSyntaxException e) {
                // 返回结果读取异常
                throw new DataException(context.getString(R.string.http_data_explain_error), e);
            }

            if (result instanceof HttpData) {
                HttpData model = (HttpData) result;
                if (model.getCode() == 0) {
                    // 代表执行成功
                    return result;
                } else if (model.getCode() == 1001) {
                    // 代表登录失效，需要重新登录
                    throw new TokenException(context.getString(R.string.http_account_error));
                } else {
                    // 代表执行失败
                    throw new ResultException(model.getMessage(), model);
                }
            }
        }
        return result;
    }

    @Override
    public Exception requestFail(Context context, Exception e) {
        // 判断这个异常是不是自己抛的
        if (e instanceof HttpException) {
            if (e instanceof TokenException) {
                // 登录信息失效，跳转到登录页

            }
        } else {
            if (e instanceof SocketTimeoutException) {
                e = new TimeoutException(context.getString(R.string.http_server_out_time), e);
            } else if (e instanceof UnknownHostException) {
                NetworkInfo info = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                // 判断网络是否连接
                if (info != null && info.isConnected()) {
                    // 有连接就是服务器的问题
                    e = new ServerException(context.getString(R.string.http_server_error), e);
                } else {
                    // 没有连接就是网络异常
                    e = new NetworkException(context.getString(R.string.http_network_error), e);
                }
            } else if (e instanceof IOException) {
                //e = new CancelException(context.getString(R.string.http_request_cancel), e);
                e = new CancelException("", e);
            }else {
                e = new HttpException(e.getMessage(), e);
            }
        }

        // 打印错误信息
        EasyLog.print(e);
        return e;
    }
}