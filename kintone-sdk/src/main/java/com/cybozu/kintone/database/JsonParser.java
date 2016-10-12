//   Copyright 2013 Cybozu
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//
//   Google Gson
//   Copyright (c) 2008-2009 Google Inc. 
//
//   Licensed under the Apache License, Version 2.0 (the "License"); 
//   you may not use this file except in compliance with the License. 
//   You may obtain a copy of the License at 
//
//       http://www.apache.org/licenses/LICENSE-2.0 
//
//   Unless required by applicable law or agreed to in writing, software 
//   distributed under the License is distributed on an "AS IS" BASIS, 
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
//   See the License for the specific language governing permissions and 
//   limitations under the License.

package com.cybozu.kintone.database;

import com.cybozu.kintone.database.exception.TypeMismatchException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * JsonParser class converts JSON string to Java object and also Java object to
 * JSON string. Although this class uses Google Gson library, developers don't
 * have to care about that. See the detail of Google Gson.
 * https://code.google.com/p/google-gson/
 */
public class JsonParser {

    public JsonParser() {

    }

    /**
     * Converts the json string to the error response object.
     * @param json
     *            a json string
     * @return error response object
     */
    public ErrorResponse jsonToErrorResponse(String json) {
        Gson gson = new Gson();
        try {
            return gson.fromJson(json, ErrorResponse.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
    
    /**
     * Converts the json string to the resultset object.
     * @param con
     *            a connection object
     * @param json
     *            a json string
     * @return resultset object
     * @throws IOException
     */
    public ResultSet jsonToResultSet(Connection con, String json)
            throws IOException {

        ResultSet rs = new ResultSet(con);
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        if (root.isJsonObject()) {
        	JsonObject rootObject = root.getAsJsonObject();
            JsonArray records = rootObject.get("records").getAsJsonArray();
            for (JsonElement elem: records) {
                Record record = readRecord(elem);
                if (record != null) {
                    rs.add(record);
                }
            }
            
            if (!rootObject.get("totalCount").isJsonNull()) {
            	rs.setTotalCount(rootObject.get("totalCount").getAsLong());
            	
            }
        }
        
        
        return rs;
    }
    
    /**
     * Reads and parses each record element.
     * @param elem
     *            a json element represents a record object
     * @return the record object created
     * @throws IOException
     */
    private Record readRecord(JsonElement elem) throws IOException {

        Record record = new Record();

        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            Set<Map.Entry<String,JsonElement>> set = obj.entrySet();
            for (Map.Entry<String,JsonElement> entry: set) {
                Field field = readField(entry.getKey(), entry.getValue());
                if (field != null) {
                    record.addField(field.getName(), field);
                }
            }
        }

        return record;
    }

    /**
     * Reads and parses each field element.
     * @param fieldName
     *            the field name
     * @param elem
     *            a json element represents a record object
     * @return the field object created
     * @throws IOException
     */
    private Field readField(String fieldName, JsonElement fieldElem) throws IOException {

        Field field = null;

        
        if (!fieldElem.isJsonObject()) return null;
        JsonObject obj = fieldElem.getAsJsonObject();
        
        FieldType type = FieldType.getEnum(obj.get("type").getAsString());
        JsonElement element = obj.get("value");

        Object object = null;
        String strVal = null;
        if (type == null || element == null)
            return null;

        if (!element.isJsonNull()) {
            switch (type) {
            case SINGLE_LINE_TEXT:
            case CALC:
            case MULTI_LINE_TEXT:
            case RICH_TEXT:
            case RADIO_BUTTON:
            case DROP_DOWN:
            case LINK:
            case STATUS:
            case RECORD_NUMBER:
            case NUMBER:
                object = element.getAsString();
                break;
            case __ID__:
            case __REVISION__:
                strVal = element.getAsString();
                try {
                    object = Long.valueOf(strVal);
                } catch (NumberFormatException e) {
                }
                break;
            case DATE:
            case TIME:
            case DATETIME:
            case CREATED_TIME:
            case UPDATED_TIME:
                object = element.getAsString();
                break;
            case CHECK_BOX:
            case MULTI_SELECT:
            case CATEGORY:
                object = jsonToStringArray(element);
                break;
            case FILE:
                object = jsonToFileArray(element);
                break;
            case CREATOR:
            case MODIFIER:
                if (element.isJsonObject()) {
                    Gson gson = new Gson();
                    object = gson.fromJson(element, UserDto.class);
                }
                break;
            case USER_SELECT:
            case ORGANIZATION_SELECT:
            case GROUP_SELECT:
            case STATUS_ASSIGNEE:
                object = jsonToUserArray(element);
                break;
            case SUBTABLE:
                object = jsonToSubtable(element);
                break;
            }
        }
        field = new Field(fieldName, type, object);

        return field;
    }

    /**
     * Converts json element to the sub table object.
     * @param element json element
     * @return sub table object
     * @throws IOException
     */
    private List<Record> jsonToSubtable(JsonElement element) throws IOException {
        List<Record> rs = new ArrayList<Record>();
        
        if (!element.isJsonArray()) return null;
        
        JsonArray records = element.getAsJsonArray();
        for (JsonElement elem: records) {
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                String id = obj.get("id").getAsString();
                JsonElement value = obj.get("value");
                Record record = readRecord(value);
                if (record != null) {
                    try {
                        record.setId(Long.valueOf(id));
                    } catch (NumberFormatException e) {
                    }
                    rs.add(record);
                }
            }
        }
        
        return rs;
    }
    
    /**
     * Converts json element to the string array object.
     * @param element json element
     * @return string array object
     * @throws IOException
     */
    private List<String> jsonToStringArray(JsonElement element) {
        if (!element.isJsonArray())
            return null;
        Type collectionType = new TypeToken<Collection<String>>() {
        }.getType();
        Gson gson = new Gson();

        return gson.fromJson(element, collectionType);
    }

    /**
     * Converts json element to the user array object.
     * @param element json element
     * @return user array object
     * @throws IOException
     */
    private List<UserDto> jsonToUserArray(JsonElement element) {
        if (!element.isJsonArray())
            return null;
        Type collectionType = new TypeToken<Collection<UserDto>>() {
        }.getType();
        Gson gson = new Gson();

        return gson.fromJson(element, collectionType);
    }

    /**
     * Converts json element to the file array object.
     * @param element json element
     * @return file array object
     * @throws IOException
     */
    private List<FileDto> jsonToFileArray(JsonElement element) {
        if (!element.isJsonArray())
            return null;
        Type collectionType = new TypeToken<Collection<FileDto>>() {
        }.getType();
        Gson gson = new Gson();

        return gson.fromJson(element, collectionType);
    }

    /**
     * Writes the field object with json writer.
     * @param writer json writer
     * @param field field object
     * @throws IOException
     */
    private void writeField(JsonWriter writer, Field field) throws IOException {
        writer.name(field.getName());
        writer.beginObject();
        writeFieldValue(writer, field);
        writer.endObject();
    }

    /**
     * Writes the field value with json writer.
     * @param writer json writer
     * @param field field object
     * @throws IOException
     */
    private void writeFieldValue(JsonWriter writer, Field field) throws IOException {
    	writer.name("value");
        FieldType type = field.getFieldType();

        if (field.isEmpty()) {
            writer.value("");
        } else {

            switch (type) {
            case SINGLE_LINE_TEXT:
            case CALC:
            case MULTI_LINE_TEXT:
            case RICH_TEXT:
            case RADIO_BUTTON:
            case DROP_DOWN:
            case LINK:
            case STATUS:
                writer.value(field.getAsString());
                break;
            case NUMBER:
            case RECORD_NUMBER:
                writer.value(String.valueOf(field.getAsLong()));
                break;
            case DATE:
            case TIME:
            case DATETIME:
            case CREATED_TIME:
            case UPDATED_TIME:
                writer.value(field.getAsString());
                break;
            case CHECK_BOX:
            case MULTI_SELECT:
            case CATEGORY:
                writer.beginArray();
                Iterable<String> strList = field.getAsStringList();
                for (String strVal : strList) {
                    writer.value(strVal);
                }
                writer.endArray();
                break;
            case FILE:
                writer.beginArray();
                Iterable<FileDto> fileList = field.getAsFileList();
                for (FileDto file : fileList) {
                    writer.beginObject();
                    writer.name("fileKey").value(file.getFileKey());
                    writer.endObject();
                }
                writer.endArray();
                break;
            case USER_SELECT:
            case ORGANIZATION_SELECT:
            case GROUP_SELECT:
                writer.beginArray();
                Iterable<UserDto> userList = field.getAsUserList();
                for (UserDto user : userList) {
                    writer.beginObject();
                    writer.name("code").value(user.getCode());
                    writer.endObject();
                }
                writer.endArray();
                break;
            case CREATOR:
            case MODIFIER:
                writer.beginObject();
                writer.name("code").value(field.getAsUserInfo().getCode());
                writer.endObject();
                break;
            case SUBTABLE:
                writer.beginArray();
                List<Record> subtable = field.getAsSubtable();
                writeSubtable(writer, subtable);
                writer.endArray();
                break;
            default:
                writer.value("");
            }
        }	
    }
    
    /**
     * Writes the subtable value to json.
     * @param writer
     *            a json writer
     * @param subtable
     *            a subtable object
     */
    private void writeSubtable(JsonWriter writer, Iterable<Record> subtable) throws IOException {
        for (Record record : subtable) {
            writer.beginObject();
            //writer.name("id").value(record.getId());
            writer.name("value");
            writer.beginObject();
            for (String fieldName : record.getFieldNames()) {
                Field field = record.getField(fieldName);
                try {
                    writeField(writer, field);
                } catch (TypeMismatchException e) {
                    e.printStackTrace();
                }
            }
            writer.endObject();
            writer.endObject();
        }
    }
    /**
     * Generates the json string for insert method.
     * @param app
     *            the application id
     * @param records
     *            the array of the record object
     * @return the json string
     * @throws IOException
     */
    public String recordsToJsonForInsert(long app, List<Record> records)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("records");

        writer.beginArray();
        for (Record record : records) {
            writer.beginObject();
            Set<Map.Entry<String,Field>> set = record.getEntrySet();
            for (Map.Entry<String,Field> entry: set) {
                Field field = entry.getValue();
                try {
                    writeField(writer, field);
                } catch (TypeMismatchException e) {
                    e.printStackTrace();
                }
            }
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }

    /**
     * Retrieves the array of the Long values from json.
     * @param json
     *            a json string
     * @return the array of the long value
     */
    public List<Long> jsonToLongArray(String json) {
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Long>>() {}.getType();
        return gson.fromJson(json, listType);
    }

    /**
     * Retrieves the array of the ids from json string.
     * @param json
     *            a json string
     * @return the array of the id
     * @throws IOException
     */
    public List<Long> jsonToIDs(String json) throws IOException {
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        List<Long> ids = new ArrayList<Long>();
        if (root.isJsonObject()) {
            JsonArray jsonIds = root.getAsJsonObject().get("ids").getAsJsonArray();
            for (JsonElement elem: jsonIds) {
                Long id = new Long(elem.getAsString());
                ids.add(id);
            }
        }
        
        return ids;
    }

    /**
     * Generates the json string for update method.
     * @param app
     *            the application id
     * @param ids
     *            the array of the record id to be updated
     * @param record
     *            the values of updated records
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForUpdate(long app, List<Long> ids, Record record)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("records");

        writer.beginArray();
        for (long id : ids) {
            writer.beginObject();
            writer.name("id").value(id);
            if (record.hasRevision()) {
                writer.name("revision").value(record.getRevision());
            }
            writer.name("record");
            writer.beginObject();
            for (String fieldName : record.getFieldNames()) {
                Field field = record.getField(fieldName);
                try {
                    writeField(writer, field);
                } catch (TypeMismatchException e) {
                    e.printStackTrace();
                }
            }
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string for update method.
     * @param app
     *            application id
     * @param record
     *            updated record
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForUpdate(long app, Record record)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("id").value(record.getId());
        if (record.hasRevision()) {
            writer.name("revision").value(record.getRevision());
        }
        writer.name("record");
        writer.beginObject();
        for (String fieldName : record.getFieldNames()) {
            Field field = record.getField(fieldName);
            try {
                writeField(writer, field);
            } catch (TypeMismatchException e) {
                e.printStackTrace();
            }
        }
        writer.endObject();
        
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string for update method.
     * @param app
     *            the application id
     * @param records
     *            an array of the updated records
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForUpdate(long app, List<Record> records)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("records");

        writer.beginArray();
        for (Record record : records) {
            writer.beginObject();
            writer.name("id").value(record.getId());
            if (record.hasRevision()) {
                writer.name("revision").value(record.getRevision());
            }
            writer.name("record");
            writer.beginObject();
            for (String fieldName : record.getFieldNames()) {
                Field field = record.getField(fieldName);
                try {
                    writeField(writer, field);
                } catch (TypeMismatchException e) {
                    e.printStackTrace();
                }
            }
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string for update method.
     * @param app
     *            application id
     * @param key
     *            key field name
     * @param record
     *            updated record
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForUpdateByKey(long app, String key, Record record)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        if (record.hasRevision()) {
            writer.name("revision").value(record.getRevision());
        }
        writer.name("updateKey");
        writer.beginObject();
        writer.name("field").value(key);
        writeFieldValue(writer, record.getField(key));
        writer.endObject();
        
        writer.name("record");
        writer.beginObject();
        for (String fieldName : record.getFieldNames()) {
        	if (fieldName == key) continue;
        	
            Field field = record.getField(fieldName);
            try {
                writeField(writer, field);
            } catch (TypeMismatchException e) {
                e.printStackTrace();
            }
        }
        writer.endObject();
        
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string for update method.
     * @param app
     *            application id
     * @param key
     *            key field name
     * @param records
     *            an array of the updated records
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForUpdateByKey(long app, String key, List<Record> records)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("records");

        writer.beginArray();
        for (Record record : records) {
            writer.beginObject();
            if (record.hasRevision()) {
                writer.name("revision").value(record.getRevision());
            }
            writer.name("updateKey");
            writer.beginObject();
            writer.name("field").value(key);
            writeFieldValue(writer, record.getField(key));
            writer.endObject();
            
            writer.name("record");
            writer.beginObject();
            for (String fieldName : record.getFieldNames()) {
            	if (fieldName == key) continue;
            	
                Field field = record.getField(fieldName);
                try {
                    writeField(writer, field);
                } catch (TypeMismatchException e) {
                    e.printStackTrace();
                }
            }
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string for delete method.
     * @param app
     *            the application id
     * @param records
     *            an array of the records to be deleted
     * @return
     *        json string
     * @throws IOException
     */
    public String recordsToJsonForDelete(long app, List<Record> records)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        
        writer.name("ids");
        writer.beginArray();
        for (Record record : records) {
            writer.value(record.getId());
        }
        writer.endArray();
        
        writer.name("revisions");
        writer.beginArray();
        for (Record record : records) {
            writer.value(record.getRevision());
        }
        writer.endArray();

        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Retrieves the file key string from json string.
     * @param json
     *            a json string
     * @return the file key
     * @throws IOException
     */
    public String jsonToFileKey(String json) throws IOException {
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        String fileKey = null;
        if (root.isJsonObject()) {
            fileKey = root.getAsJsonObject().get("fileKey").getAsString();
        }
        
        return fileKey;
    }
    
    /**
     * Retrieves the revision string from json string.
     * @param json
     *            a json string
     * @return the revision number
     * @throws IOException
     */
    public long jsonToRevision(String json) throws IOException {
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        String revision = null;
        if (root.isJsonObject()) {
        	revision = root.getAsJsonObject().get("revision").getAsString();
        }
        
        return Long.valueOf(revision);
    }
    
    /**
     * Retrieves the id string from json string.
     * @param json
     *            a json string
     * @return the id number
     * @throws IOException
     */
    public long jsonToId(String json) throws IOException {
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        String id = null;
        if (root.isJsonObject()) {
        	id = root.getAsJsonObject().get("id").getAsString();
        }
        
        return Long.valueOf(id);
    }
    
    /**
     * Convert json string to AppDto.
     * @param json
     *            a json string
     * @return app object
     * @throws IOException
     */
    public AppDto jsonToApp(String json) throws IOException {
    	com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement element = parser.parse(json);
        Type elementType = new TypeToken<AppDto>() {
        }.getType();
        Gson gson = new Gson();

        return gson.fromJson(element, elementType);
    }
    
    /**
     * Convert json string to AppDto array.
     * @param json
     *            a json string
     * @return the list of app object
     * @throws IOException
     */
    public List<AppDto> jsonToApps(String json) throws IOException {
    	com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        if (!root.isJsonObject()) 
        	return null;
        
        JsonArray apps = root.getAsJsonObject().get("apps").getAsJsonArray();
            
        if (!apps.isJsonArray())
            return null;
        
        Type collectionType = new TypeToken<Collection<AppDto>>() {
        }.getType();
        Gson gson = new Gson();

        return gson.fromJson(apps, collectionType);
    }
    
    /**
     * Generates the json string to update assignees.
     * @param id
     *            record id
     * @param code
     *            array of the code of the assigned users
     * @param revision
     *            revision number (-1 means "not set")
     * @return
     *        json string
     * @throws IOException
     */
    public String generateForUpdateAssignees(long app, long id, List<String> codes, long revision)
    throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("id").value(id);
        
        
        writer.name("assignees");
        writer.beginArray();
        for (String code : codes) {
            writer.value(code);
        }
        writer.endArray();
        
        if (revision >= 0) {
        	writer.name("revision").value(revision);
        }
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string to update status.
     * @param app
     *            application id
     * @param id
     *            record id
     * @param action
     *            action name
     * @param assignee
     *            login name of the assignee
     * @param revision
     *            revision number (-1 means "not set")
     * @return
     *        json string
     * @throws IOException
     */
    public String generateForUpdateStatus(long app, long id, String action, String assignee, long revision)
    throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("id").value(id);
        writer.name("action").value(action);
        if (assignee != null) {
        	writer.name("assignee").value(assignee);
        }
        
        if (revision >= 0) {
        	writer.name("revision").value(revision);
        }
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string to update status(for bulk updating).
     * @param app
     *            application id
     * @param ids
     *            an array of the record id
     * @param actions
     *            an array of the action name
     * @param assignees
     *            an array of the login name of the assignee
     * @param revision
     *            an array of the revision number (-1 means "not set")
     * @return
     *        json string
     * @throws IOException
     */
    public String generateForUpdateStatus(long app, List<Long> ids, List<String> actions, List<String> assignees, List<Long> revisions)
    throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        
        int size = ids.size();
        writer.name("records");
        writer.beginArray();
        for (int i = 0; i < size; i++) {
        	writer.beginObject();
        	writer.name("id").value(ids.get(i));
            writer.name("action").value(actions.get(i));
            if (assignees.get(i) != null) {
            	writer.name("assignee").value(assignees.get(i));
            }
            
            if (revisions.get(i) >= 0) {
            	writer.name("revision").value(revisions.get(i));
            }
        	writer.endObject();
        }
        writer.endArray();
        
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string to add comment.
     * @param app
     *            application id
     * @param record
     *            record id
     * @param mentions
     *            an array of mentions
     * @return
     *        json string
     * @throws IOException
     */
    public String generateForAddComment(long app, long record, String text, List<MentionDto> mentions)
    throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("record").value(record);
        
        writer.name("comment");
        writer.beginObject();
        
        writer.name("text").value(text);
        
        writer.name("mentions");
        writer.beginArray();
        for (MentionDto mention: mentions) {
        	writer.beginObject();
        	writer.name("code").value(mention.getCode());
        	writer.name("type").value(mention.getType());
        	writer.endObject();
        }
        writer.endArray();
        writer.endObject();
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Generates the json string to delete comment.
     * @param app
     *            application id
     * @param record
     *            record id
     * @param id
     *            comment id
     * @return
     *        json string
     * @throws IOException
     */
    public String generateForDeleteComment(long app, long record, long id)
    throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(baos));

        writer.beginObject();
        writer.name("app").value(app);
        writer.name("record").value(record);
        writer.name("comment").value(id);
        writer.endObject();

        writer.close();
        return new String(baos.toByteArray());
    }
    
    /**
     * Converts the json string to the commentset object.
     * @param json
     *            a json string
     * @return commentset object
     * @throws IOException
     */
    public CommentSet jsonToCommentSet(Connection con, String json)
            throws IOException {

        CommentSet cs = new CommentSet();
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        JsonElement root = parser.parse(json);
        
        if (root.isJsonObject()) {
        	JsonObject obj = root.getAsJsonObject();
            JsonArray comments = obj.get("comments").getAsJsonArray();
            for (JsonElement elem: comments) {
                Comment comment = readComment(elem);
                if (comment != null) {
                    cs.add(comment);
                }
            }
            
            cs.setNewer(obj.get("newer").getAsBoolean());
            cs.setOlder(obj.get("older").getAsBoolean());
        }
        return cs;
    }
    
    private Date getDateTime(String strDate) {
        if (strDate == null || strDate.isEmpty())
            return null;
        try {
            DateFormat df;
            if (strDate.indexOf('.') > 0) {
            	df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'");
            } else {
            	df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            }
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df.parse(strDate);
        } catch (ParseException e) {
            throw new TypeMismatchException();
        }
    }
    /**
     * Reads and parses each comment element.
     * @param elem
     *            a json element represents a comment object
     * @return the comment object created
     * @throws IOException
     */
    private Comment readComment(JsonElement elem) throws IOException {

        Comment comment = null;
        Gson gson = new Gson();

        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            long id = obj.get("id").getAsLong();
            String text = obj.get("text").getAsString();
            Date createdAt = getDateTime(obj.get("createdAt").getAsString());
            
            Type userElementType = new TypeToken<UserDto>() {
            }.getType();
            
            UserDto user = gson.fromJson(obj.getAsJsonObject("creator"), userElementType);
                       
            Type mentionsElementType = new TypeToken<Collection<MentionDto>>() {
            }.getType();
            
            List<MentionDto> mentions = gson.fromJson(obj.getAsJsonArray("mentions"), mentionsElementType);
            
            comment = new Comment(id, text, createdAt, user, mentions);
        }

        return comment;
    }
} 
