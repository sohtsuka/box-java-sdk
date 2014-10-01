package com.box.sdk;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public final class BoxFolder extends BoxItem implements Iterable<BoxItem> {
    private static final String UPLOAD_FILE_URL_BASE = "https://upload.box.com/api/2.0/";
    private static final URLTemplate CREATE_FOLDER_URL = new URLTemplate("folders");
    private static final URLTemplate COPY_FOLDER_URL = new URLTemplate("folders/%s/copy");
    private static final URLTemplate DELETE_FOLDER_URL = new URLTemplate("folders/%s?recursive=%b");
    private static final URLTemplate FOLDER_INFO_URL_TEMPLATE = new URLTemplate("folders/%s");
    private static final URLTemplate UPLOAD_FILE_URL = new URLTemplate("files/content");
    private static final URLTemplate ADD_COLLABORATION_URL = new URLTemplate("collaborations");

    private final URL folderURL;

    public BoxFolder(BoxAPIConnection api, String id) {
        super(api, id);

        this.folderURL = FOLDER_INFO_URL_TEMPLATE.build(this.getAPI().getBaseURL(), this.getID());
    }

    public static BoxFolder getRootFolder(BoxAPIConnection api) {
        return new BoxFolder(api, "0");
    }

    public BoxCollaboration.Info addCollaborator(BoxUser user, BoxCollaboration.Role role) {
        JsonObject accessibleByField = new JsonObject();
        accessibleByField.add("id", user.getID());
        accessibleByField.add("type", "user");

        return this.addCollaborator(accessibleByField, role);
    }

    public BoxCollaboration.Info addCollaborator(String email, BoxCollaboration.Role role) {
        JsonObject accessibleByField = new JsonObject();
        accessibleByField.add("login", email);
        accessibleByField.add("type", "user");

        return this.addCollaborator(accessibleByField, role);
    }

    private BoxCollaboration.Info addCollaborator(JsonObject accessibleByField, BoxCollaboration.Role role) {
        BoxAPIConnection api = this.getAPI();
        URL url = ADD_COLLABORATION_URL.build(api.getBaseURL());

        JsonObject itemField = new JsonObject();
        itemField.add("id", this.getID());
        itemField.add("type", "folder");

        JsonObject requestJSON = new JsonObject();
        requestJSON.add("item", itemField);
        requestJSON.add("accessible_by", accessibleByField);
        requestJSON.add("role", role.toJSONString());

        BoxJSONRequest request = new BoxJSONRequest(api, url, "POST");
        request.setBody(requestJSON.toString());
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        JsonObject responseJSON = JsonObject.readFrom(response.getJSON());

        BoxCollaboration newCollaboration = new BoxCollaboration(api, responseJSON.get("id").asString());
        BoxCollaboration.Info info = newCollaboration.new Info(responseJSON);
        return info;
    }

    public BoxFolder.Info getInfo() {
        BoxAPIRequest request = new BoxAPIRequest(this.getAPI(), this.folderURL, "GET");
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        return new Info(response.getJSON());
    }

    public BoxFolder.Info getInfo(String... fields) {
        String queryString = new QueryStringBuilder().addFieldsParam(fields).toString();
        URL url = FOLDER_INFO_URL_TEMPLATE.buildWithQuery(this.getAPI().getBaseURL(), queryString, this.getID());

        BoxAPIRequest request = new BoxAPIRequest(this.getAPI(), url, "GET");
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        return new Info(response.getJSON());
    }

    public void updateInfo(BoxFolder.Info info) {
        BoxJSONRequest request = new BoxJSONRequest(this.getAPI(), this.folderURL, "PUT");
        request.setBody(info.getPendingChanges());
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        JsonObject jsonObject = JsonObject.readFrom(response.getJSON());
        info.update(jsonObject);
    }

    public BoxFolder.Info copy(BoxFolder destination) {
        return this.copy(destination, null);
    }

    public BoxFolder.Info copy(BoxFolder destination, String newName) {
        URL url = COPY_FOLDER_URL.build(this.getAPI().getBaseURL(), this.getID());
        BoxJSONRequest request = new BoxJSONRequest(this.getAPI(), url, "POST");

        JsonObject parent = new JsonObject();
        parent.add("id", destination.getID());

        JsonObject copyInfo = new JsonObject();
        copyInfo.add("parent", parent);
        if (newName != null) {
            copyInfo.add("name", newName);
        }

        request.setBody(copyInfo.toString());
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        JsonObject responseJSON = JsonObject.readFrom(response.getJSON());
        BoxFolder copiedFolder = new BoxFolder(this.getAPI(), responseJSON.get("id").asString());
        return copiedFolder.new Info(responseJSON);
    }

    public BoxFolder createFolder(String name) {
        JsonObject parent = new JsonObject();
        parent.add("id", this.getID());

        JsonObject newFolder = new JsonObject();
        newFolder.add("name", name);
        newFolder.add("parent", parent);

        BoxJSONRequest request = new BoxJSONRequest(this.getAPI(), CREATE_FOLDER_URL.build(this.getAPI().getBaseURL()),
            "POST");
        request.setBody(newFolder.toString());
        BoxJSONResponse response = (BoxJSONResponse) request.send();
        JsonObject createdFolder = JsonObject.readFrom(response.getJSON());

        return new BoxFolder(this.getAPI(), createdFolder.get("id").asString());
    }

    public void delete(boolean recursive) {
        URL url = DELETE_FOLDER_URL.build(this.getAPI().getBaseURL(), this.getID(), recursive);
        BoxAPIRequest request = new BoxAPIRequest(this.getAPI(), url, "DELETE");
        BoxAPIResponse response = request.send();
        response.disconnect();
    }

    public void move(BoxFolder destination) {
        this.move(destination.getID());
    }

    public void move(String destinationID) {
        BoxJSONRequest request = new BoxJSONRequest(this.getAPI(), this.folderURL, "PUT");

        JsonObject parent = new JsonObject();
        parent.add("id", destinationID);

        JsonObject updateInfo = new JsonObject();
        updateInfo.add("parent", parent);

        request.setBody(updateInfo.toString());
        BoxAPIResponse response = request.send();
        response.disconnect();
    }

    public void rename(String newName) {
        BoxJSONRequest request = new BoxJSONRequest(this.getAPI(), this.folderURL, "PUT");

        JsonObject updateInfo = new JsonObject();
        updateInfo.add("name", newName);

        request.setBody(updateInfo.toString());
        BoxAPIResponse response = request.send();
        response.disconnect();
    }

    public BoxFile uploadFile(InputStream fileContent, String name) {
        return this.uploadFile(fileContent, name, null, null);
    }

    public BoxFile uploadFile(InputStream fileContent, String name, Date created, Date modified) {
        URL uploadURL = UPLOAD_FILE_URL.build(UPLOAD_FILE_URL_BASE);
        BoxMultipartRequest request = new BoxMultipartRequest(getAPI(), uploadURL);
        request.putField("parent_id", getID());
        request.setFile(fileContent, name);

        if (created != null) {
            request.putField("content_created_at", created);
        }

        if (modified != null) {
            request.putField("content_modified_at", modified);
        }

        BoxJSONResponse response = (BoxJSONResponse) request.send();
        JsonObject collection = JsonObject.readFrom(response.getJSON());
        JsonArray entries = collection.get("entries").asArray();
        String uploadedFileID = entries.get(0).asObject().get("id").asString();

        return new BoxFile(getAPI(), uploadedFileID);
    }

    public Iterator<BoxItem> iterator() {
        return new BoxItemIterator(BoxFolder.this.getAPI(), BoxFolder.this.getID());
    }

    public class Info extends BoxItem.Info<BoxFolder> {
        public Info() {
            super();
        }

        public Info(String json) {
            super(json);
        }

        protected Info(JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public BoxFolder getResource() {
            return BoxFolder.this;
        }

        @Override
        protected void parseJSONMember(JsonObject.Member member) {
            super.parseJSONMember(member);

            String memberName = member.getName();
            switch (memberName) {
                default:
                    break;
            }
        }
    }
}
