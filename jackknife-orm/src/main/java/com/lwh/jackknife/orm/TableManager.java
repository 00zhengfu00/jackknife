/*
 * Copyright (C) 2017 The JackKnife Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lwh.jackknife.orm;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.lwh.jackknife.orm.constraint.AssignType;
import com.lwh.jackknife.orm.constraint.Check;
import com.lwh.jackknife.orm.constraint.Default;
import com.lwh.jackknife.orm.constraint.NotNull;
import com.lwh.jackknife.orm.constraint.PrimaryKey;
import com.lwh.jackknife.orm.constraint.Unique;
import com.lwh.jackknife.orm.exception.ConstraintException;
import com.lwh.jackknife.orm.table.Column;
import com.lwh.jackknife.orm.table.Ignore;
import com.lwh.jackknife.orm.table.Table;
import com.lwh.jackknife.orm.type.BaseDataType;
import com.lwh.jackknife.orm.type.BooleanType;
import com.lwh.jackknife.orm.type.ByteArrayType;
import com.lwh.jackknife.orm.type.ByteType;
import com.lwh.jackknife.orm.type.CharType;
import com.lwh.jackknife.orm.type.ClassType;
import com.lwh.jackknife.orm.type.DoubleType;
import com.lwh.jackknife.orm.type.FloatType;
import com.lwh.jackknife.orm.type.IntType;
import com.lwh.jackknife.orm.type.LongType;
import com.lwh.jackknife.orm.type.ShortType;
import com.lwh.jackknife.orm.type.SqlType;
import com.lwh.jackknife.orm.type.StringType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TableManager {

    private static TableManager sInstance;

    private final char A = 'A';

    private final char Z = 'Z';

    private final String CREATE_TABLE = "CREATE TABLE";

    private final String ALTER_TABLE = "ALTER TABLE";

    private final String DROP_TABLE = "DROP TABLE";

    private final String IF_NOT_EXISTS = "IF NOT EXISTS";

    private final String ADD_COLUMN = "ADD COLUMN";

    private final String AUTO_INCREMENT = "AUTOINCREMENT";

    private final String SPACE = " ";

    private final String SINGLE_QUOTES = "\'";

    private final String UNIQUE = "UNIQUE";

    private final String DEFAULT = "DEFAULT";

    private final String CHECK = "CHECK";

    private final String NOT_NULL = "NOT NULL";

    private final String PRIMARY_KEY = "PRIMARY KEY";

    private final String LEFT_PARENTHESIS = "(";

    private final String RIGHT_PARENTHESIS = ")";

    private final String COMMA = ",";

    private final String SEMICOLON = ";";

    private final String UNDERLINE = "_";

    private final String TABLE_NAME_HEADER = "t" + UNDERLINE;

    private TableManager() {
    }

    public static TableManager getInstance() {
        if (sInstance == null) {
            synchronized (TableManager.class) {
                if (sInstance == null) {
                    sInstance = new TableManager();
                }
            }
        }
        return sInstance;
    }

    public <T extends OrmTable> String getTableName(Class<T> tableClass) {
        Table table = tableClass.getAnnotation(Table.class);
        String tableName;
        if (table != null) {
            tableName = table.value();
        } else {
            String className = tableClass.getSimpleName();
            tableName = generateTableName(className);
        }
        return tableName;
    }

    public String getColumnName(Field field) {
        String columnName;
        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            columnName = column.value();
        } else {
            String fieldName = field.getName();
            columnName = generateColumnName(fieldName);
        }
        return columnName;
    }

    public String generateTableName(String className) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            if (className.charAt(i) >= A && className.charAt(i) <= Z && i != 0) {
                sb.append(UNDERLINE);
            }
            sb.append(String.valueOf(className.charAt(i)).toLowerCase(Locale.ENGLISH));
        }
        return TABLE_NAME_HEADER + sb.toString().toLowerCase();
    }

    public String generateColumnName(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            if (fieldName.charAt(i) >= A && fieldName.charAt(i) <= Z && i != 0) {
                sb.append(UNDERLINE);
            }
            sb.append(String.valueOf(fieldName.charAt(i)).toLowerCase(Locale.ENGLISH));
        }
        return sb.toString().toLowerCase();
    }

    protected List<BaseDataType> getDeclaredDataTypes() {
        List<BaseDataType> dataTypes = new ArrayList<>();
        dataTypes.add(BooleanType.getInstance());
        dataTypes.add(ByteType.getInstance());
        dataTypes.add(ShortType.getInstance());
        dataTypes.add(IntType.getInstance());
        dataTypes.add(LongType.getInstance());
        dataTypes.add(FloatType.getInstance());
        dataTypes.add(DoubleType.getInstance());
        dataTypes.add(CharType.getInstance());
        dataTypes.add(StringType.getInstance());
        dataTypes.add(ClassType.getInstance());
        return dataTypes;
    }

    private BaseDataType matchDataType(Field field) {
        List<BaseDataType> dataTypes = getDeclaredDataTypes();
        for (BaseDataType dataType : dataTypes) {
            if (dataType.matches(field)) {
                return dataType;
            }
        }
        return ByteArrayType.getInstance();
    }

    public static <T extends OrmTable> void createTable(Class<T> tableClass) {
        if (Orm.isPrepared()) {
            getInstance()._createTable(tableClass, Orm.getDatabase());
        }
    }

    private <A extends Annotation> boolean checkColumnConstraint(Field field, Class<A> annotationType) {
        A annotation = field.getAnnotation(annotationType);
        return annotation != null;
    }

    private <A extends Annotation, V> V getColumnConstraintValue(Field field, Class<A> annotationType,
                                                                 Class<V> valueType) {
        V value = null;
        A annotation = field.getAnnotation(annotationType);
        if (Default.class.isAssignableFrom(annotationType)) {
            value = (V) ((Default) annotation).value();
        }
        if (Check.class.isAssignableFrom(annotationType)) {
            value = (V) ((Check) annotation).value();
        }
        if (PrimaryKey.class.isAssignableFrom(annotationType)) {
            value = (V) ((PrimaryKey) annotation).value();
        }
        return value;
    }

    private class ColumnBuilder {

        private StringBuilder mBuilder;
        private Field mField;
        boolean isPrimaryKey = false;

        public ColumnBuilder(Field field) {
            this.mField = field;
            this.mBuilder = new StringBuilder();
        }

        public ColumnBuilder(String str, Field field) {
            this.mField = field;
            this.mBuilder = new StringBuilder(str);
        }

        private ColumnBuilder append(String str) {
            this.mBuilder.append(str);
            return this;
        }

        private ColumnBuilder buildColumnUnique() {
            if (checkColumnConstraint(mField, Unique.class)) {
                mBuilder.append(SPACE).append(UNIQUE);
            }
            return this;
        }

        private ColumnBuilder buildColumnDefault() {
            if (checkColumnConstraint(mField, Default.class)) {
                String value = getColumnConstraintValue(mField, Default.class, String.class);
                try {
                    long number = Long.parseLong(value);
                    mBuilder.append(SPACE).append(DEFAULT)
                            .append(SPACE).append(SINGLE_QUOTES).append(number).append(SINGLE_QUOTES);
                } catch (NumberFormatException e) {
                    mBuilder.append(SPACE).append(DEFAULT)
                            .append(SPACE).append(SINGLE_QUOTES).append(value).append(SINGLE_QUOTES);
                }
            }
            return this;
        }

        private ColumnBuilder buildColumnCheck() {
            if (checkColumnConstraint(mField, Check.class)) {
                String value = getColumnConstraintValue(mField, Check.class, String.class);
                mBuilder.append(SPACE).append(CHECK).append(LEFT_PARENTHESIS)
                        .append(value).append(RIGHT_PARENTHESIS);
            }
            return this;
        }

        private ColumnBuilder buildColumnNotNull() {
            if (checkColumnConstraint(mField, NotNull.class)) {
                mBuilder.append(SPACE).append(NOT_NULL);
            }
            return this;
        }

        private ColumnBuilder buildColumnPrimaryKey() {
            if (checkColumnConstraint(mField, PrimaryKey.class)) {
                isPrimaryKey = true;
                mBuilder.append(SPACE).append(PRIMARY_KEY);
                AssignType assignType = getColumnConstraintValue(mField, PrimaryKey.class,
                        AssignType.class);
                if (assignType.equals(AssignType.BY_MYSELF)) {
                } else if (assignType.equals(AssignType.AUTO_INCREMENT)) {
                    mBuilder.append(SPACE).append(AUTO_INCREMENT);
                }
            }
            return this;
        }

        private String build() {
            return mBuilder.toString();
        }
    }

    private ColumnBuilder createColumnBuilder(Field field) {
        BaseDataType dataType = matchDataType(field);
        SqlType sqlType = dataType.getSqlType();
        String columnType = sqlType.name();
        String columnName = getColumnName(field);
        ColumnBuilder fieldBuilder = new ColumnBuilder(columnName + SPACE + columnType, field);
        fieldBuilder.buildColumnUnique()
                .buildColumnDefault()
                .buildColumnCheck()
                .buildColumnNotNull()
                .buildColumnPrimaryKey();
        return fieldBuilder;
    }

    /* package */ <T extends OrmTable> void _createTable(Class<T> tableClass, SQLiteDatabase db) {
        String tableName = getTableName(tableClass);
        Field[] fields = tableClass.getDeclaredFields();
        StringBuilder sqlBuilder = new StringBuilder(CREATE_TABLE + SPACE + IF_NOT_EXISTS + SPACE
                + tableName + LEFT_PARENTHESIS);//table header
        boolean hasPrimaryKey = false;
        for (Field field : fields) {
            field.setAccessible(true);
            Ignore ignore = field.getAnnotation(Ignore.class);
            if (ignore != null) {
                continue;
            }
            ColumnBuilder fieldBuilder = createColumnBuilder(field);
            if (fieldBuilder.isPrimaryKey) {
                hasPrimaryKey = true;
            }
            sqlBuilder.append(fieldBuilder.build()).append(COMMA);
        }
        if (!hasPrimaryKey) {
            throw new ConstraintException("Lack valid primary key.");
        }
        try {
            String sql = sqlBuilder.deleteCharAt(sqlBuilder.length() - 1).append(RIGHT_PARENTHESIS)
                    .append(SEMICOLON).toString();
            OrmLog.d(sql);
            db.execSQL(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static <T extends OrmTable> void upgradeTable(Class<T> tableClass) {
        if (Orm.isPrepared()) {
            getInstance()._upgradeTable(tableClass, Orm.getDatabase());
        }
    }

    /* package */ <T extends OrmTable> void _upgradeTable(Class<T> tableClass, SQLiteDatabase db) {
        String tableName = getTableName(tableClass);
        Field[] fields = tableClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Ignore ignore = field.getAnnotation(Ignore.class);
            if (ignore != null) {
                continue;
            }
            String columnName = getColumnName(field);
            try {
                String sql = ALTER_TABLE + SPACE + tableName + SPACE + IF_NOT_EXISTS + SPACE
                        + columnName + SPACE + ADD_COLUMN + SPACE
                        + createColumnBuilder(field).build() + SEMICOLON;
                OrmLog.d(sql);
                db.execSQL(sql);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /* package */ <T extends OrmTable> void _dropTable(Class<T> tableClass, SQLiteDatabase db) {
        String sql = DROP_TABLE + SPACE + getTableName(tableClass);
        OrmLog.d(sql);
        db.execSQL(sql);
    }

    public static <T extends OrmTable> void dropTable(Class<T> tableClass) {
        if (Orm.isPrepared()) {
            getInstance()._dropTable(tableClass, Orm.getDatabase());
        }
    }

    /**
     * Drop and create table.
     *
     * @since jackknife 4.2.1
     */
    public static <T extends OrmTable> void recreateTable(Class<T> tableClass) {
        if (Orm.isPrepared()) {
            getInstance()._dropTable(tableClass, Orm.getDatabase());
            getInstance()._createTable(tableClass, Orm.getDatabase());
        }
    }
}
