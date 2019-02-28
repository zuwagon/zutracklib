package zuwagon.zutracklib;

import org.json.JSONObject;

public interface ZWHttpCallback {

    void HttpErrorMsg(String msg);

    void HttpResponseMsg(JSONObject jsonObject);
}
