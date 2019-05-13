package zuwagon.zutracklib;

import java.io.Serializable;

public class Order implements Serializable {

    String _id;
    String lat ;
    String lon;

    public Order() {

    }

    public Order(String order_id, String latitude, String longitude) {
        this._id = order_id;
        this.lat = latitude;
        this.lon = longitude;
    }
}
