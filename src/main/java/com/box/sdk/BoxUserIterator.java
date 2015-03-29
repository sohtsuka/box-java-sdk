package com.box.sdk;

import java.net.URL;
import java.util.Iterator;

import com.eclipsesource.json.JsonObject;

class BoxUserIterator implements Iterator<BoxUser.Info> {
    private static final long LIMIT = 1000;

    private final BoxAPIConnection api;
    private final JSONIterator jsonIterator;

    private static final Filter<JsonObject> EMPTY_FILTER = new Filter<JsonObject>() {
        @Override
        public boolean shouldInclude(JsonObject jsonObject) {
            return true;
        }
    };

    BoxUserIterator(BoxAPIConnection api, URL url) {
        this.api = api;
        this.jsonIterator = new JSONIterator(api, url, LIMIT);
        this.jsonIterator.setFilter(EMPTY_FILTER);
    }

    public boolean hasNext() {
        return this.jsonIterator.hasNext();
    }

    public BoxUser.Info next() {
        JsonObject nextJSONObject = this.jsonIterator.next();
        String id = nextJSONObject.get("id").asString();

        BoxUser User = new BoxUser(this.api, id);
        return User.new Info(nextJSONObject);
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
