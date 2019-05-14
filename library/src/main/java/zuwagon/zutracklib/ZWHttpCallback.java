package zuwagon.zutracklib;

import org.json.JSONObject;

public interface ZWHttpCallback {

    void HttpErrorMsg(String Type ,String msg);

    void HttpResponseMsg(String Type ,JSONObject jsonObject);
}
