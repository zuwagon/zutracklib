package zuwagon.zutracklib;

import org.json.JSONObject;

public interface ZWHttpCallback {

    void HttpErrorMsg(String msg);

    void HttpResponseMsg(JSONObject jsonObject);

    void Pick_DropResponse(JSONObject object);
    void Pick_Droperror(String err);
}
