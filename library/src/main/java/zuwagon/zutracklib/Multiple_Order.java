package zuwagon.zutracklib;

class Multiple_Order {
    String _id;
    String lat;
    String lon;

    public Multiple_Order(String _id, String lat, String lon) {
        this._id = _id;
        this.lat = lat;
        this.lon = lon;
    }

    public String get_id() {
        return _id;
    }

    public String getLat() {
        return lat;
    }

    public String getLon() {
        return lon;
    }
}
