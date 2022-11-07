/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.color.gradient.impl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class GradientPoint implements Comparable<GradientPoint> {
    private final Color color;
    private final float value;

    public static GradientPoint of(Color color, float value) {
        GradientPoint gp = new GradientPoint(color, value);
        return gp;
    }

    private GradientPoint(Color color, float value) {
        this.color = color;
        this.value = value;
    }

    public Color getColor() {
        return this.color;
    }

    public float getValue() {
        return this.value;
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + ((this.color == null) ? 0 : this.color.hashCode());
        result = 31 * result + Float.floatToIntBits(this.value);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GradientPoint other = (GradientPoint) obj;
        if (this.color == null) {
            if (other.color != null)
                return false;
        } else if (!this.color.equals(other.color)) {
            return false;
        }
        if (Float.floatToIntBits(this.value) != Float.floatToIntBits(other.value))
            return false;
        return true;
    }

    public static String asString(List<GradientPoint> points) {
        return asJSON(points);
    }

    private static final String POINT_KEY = "pos";
    private static final String COLOR_KEY = "color";
    private static final String TOP_LEVEL_KEY = "gradient";

    public static List<GradientPoint> fromJSON(String data) {

        try {
            Object obj = Jsoner.deserialize(data);
            if (obj instanceof JsonArray) {
                return fromArray((JsonArray) obj);
            } else if (obj instanceof JsonObject) {
                return fromArray((JsonArray) ((JsonObject) obj).get(TOP_LEVEL_KEY));
            } else {
                throw new IllegalArgumentException("Invalid color gradient: " + data);
            }

        } catch (JsonException | ClassCastException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static List<GradientPoint> fromArray(JsonArray array) {
        List<GradientPoint> points = new ArrayList<>();
        for (Object o : array) {
            JsonObject point = (JsonObject) o;
            Color color = new Color(Integer.decode(point.get("color").toString().trim()).intValue(), false);
            float value = Float.parseFloat(point.get("pos").toString().trim());
            points.add(of(color, value));
        }
        Collections.sort(points);
        return points;
    }

    public static String asJSON(List<GradientPoint> points) {
        JsonArray list = new JsonArray();

        for (GradientPoint p : points) {
            JsonObject point = new JsonObject();
            String colorString = String.format("#%06X",
                    Integer.valueOf(p.getColor().getRGB() & 0xFFFFFF));

            point.put(COLOR_KEY, colorString);
            point.put(POINT_KEY, Float.valueOf(p.getValue()));
            list.add(point);
        }
        return list.toJson();
    }

    public int compareTo(GradientPoint o) {
        return Float.compare(this.value, o.value);
    }
}
