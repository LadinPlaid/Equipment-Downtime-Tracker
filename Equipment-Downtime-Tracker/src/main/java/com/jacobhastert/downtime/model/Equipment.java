package com.jacobhastert.downtime.model;

//Class stores four values and rejects missing or blank information
public class Equipment {

    private final String id;
    private final String name;
    private final String type;
    private final String location;

    public Equipment(String id, String name, String type, String location) {
        this.id = requireText(id, "Equipment ID");
        this.name = requireText(name, "Equipment name");
        this.type = requireText(type, "Equipment type");
        this.location = requireText(location, "Location");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank.");
        }

        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return id + " | " + name + " | " + type + " | " + location;
    }
}
