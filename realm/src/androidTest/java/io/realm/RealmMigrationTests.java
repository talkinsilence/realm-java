package io.realm;

import android.test.AndroidTestCase;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import io.realm.dynamic.RealmModifier;
import io.realm.dynamic.RealmSchema;
import io.realm.entities.AllTypes;
import io.realm.entities.Cat;
import io.realm.entities.Dog;
import io.realm.entities.Owner;
import io.realm.exceptions.RealmMigrationNeededException;
import io.realm.internal.ColumnType;
import io.realm.internal.ImplicitTransaction;
import io.realm.internal.SharedGroup;
import io.realm.internal.Table;

public class RealmMigrationTests extends AndroidTestCase {

    private ImplicitTransaction realm;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (realm != null) {
            realm.close();
        }
    }

    private ImplicitTransaction getDefaultSharedGroup() {
        String path = new File(getContext().getFilesDir(), "default.realm").getAbsolutePath();
        SharedGroup sharedGroup = new SharedGroup(path, SharedGroup.Durability.FULL, null);
        return sharedGroup.beginImplicitTransaction();
    }

    private void assertColumn(Table table, String columnName, int columnIndex, ColumnType columnType) {
        long index = table.getColumnIndex(columnName);
        assertEquals(columnIndex, index);
        assertEquals(columnType, table.getColumnType(index));
    }

    public void testRealmClosedAfterMigrationException() throws IOException {
        String REALM_NAME = "default0.realm";
        Realm.deleteRealmFile(getContext(), REALM_NAME);
        TestHelper.copyRealmFromAssets(getContext(), REALM_NAME, REALM_NAME);
        try {
            Realm.getInstance(getContext(), REALM_NAME);
            fail("A migration should be triggered");
        } catch (RealmMigrationNeededException expected) {
            Realm.deleteRealmFile(getContext(), REALM_NAME); // Delete old realm
        }

        // This should recreate the Realm with proper schema
        Realm realm = Realm.getInstance(getContext(), REALM_NAME);
        int result = realm.where(AllTypes.class).equalTo("columnString", "Foo").findAll().size();
        assertEquals(0, result);
    }

    // Create a Realm file with no Realm classes
    private void createEmptyDefaultRealm() {
        Realm.setSchema(AllTypes.class);
        Realm.deleteRealmFile(getContext());
        Realm realm = Realm.getInstance(getContext());
        realm.close();
    }

    private void createSimpleRealm() {
        Realm.setSchema(Owner.class, Dog.class, Cat.class);
        Realm.deleteRealmFile(getContext());
        Realm realm = Realm.getInstance(getContext());
        realm.close();
    }

    private String getDefaultRealmPath() {
        return new File(getContext().getFilesDir(), "default.realm").getAbsolutePath();
    }

    public void testAddEmptyClassThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                    realm.addClass(null);
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testAddClass() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                realm.addClass("Foo");
            }
        });
        realm = getDefaultSharedGroup();
        assertTrue(realm.hasTable("class_Foo"));
    }

    public void testRemoveEmptyClassThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                    realm.removeClass(null);
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRemoveLinkedClassThrows() {
        createSimpleRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                realm.removeClass("Owner");
                }
            });
            fail();
        } catch (RuntimeException expected) {
        }
    }

    public void testRemoveClass() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                realm.removeClass("AllTypes");
            }
        });
        realm = getDefaultSharedGroup();
        assertFalse(realm.hasTable("class_AllTypes"));
    }

    public void testRenameEmptyClassThrows() {
        createSimpleRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                // Test first argument is null
                try {
                    realm.renameClass(null, "Foo");
                } catch (IllegalArgumentException expected) {
                }

                // Test second argument is null
                try {
                    realm.renameClass("Foo", null);
                } catch (IllegalArgumentException expected) {
                }
            }
        });
    }

    public void testRenameClass() {
        createSimpleRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema realm, long oldVersion, long newVersion) {
                realm.renameClass("Owner", "Foo");
            }
        });
        realm = getDefaultSharedGroup();
        assertFalse(realm.hasTable("class_Owner"));
        assertTrue(realm.hasTable("class_Foo"));
    }

    public void testAddEmptyFieldThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.addClass("Foo").addString(null);
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testAddField() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                schema.addClass("Foo")
                        .addString("a")
                        .addShort("b")
                        .addInt("c")
                        .addLong("d")
                        .addBoolean("e")
                        .addFloat("f")
                        .addDouble("g")
                        .addByteArray("h")
                        .addDate("i")
                        .addObject("j", schema.getClass("AllTypes"))
                        .addList("k", schema.getClass("AllTypes"));
            }
        });

        realm = getDefaultSharedGroup();
        assertTrue(realm.hasTable("class_Foo"));
        Table table = realm.getTable("class_Foo");
        assertEquals(11, table.getColumnCount());
        assertColumn(table, "a", 0, ColumnType.STRING);
        assertColumn(table, "b", 1, ColumnType.INTEGER);
        assertColumn(table, "c", 2, ColumnType.INTEGER);
        assertColumn(table, "d", 3, ColumnType.INTEGER);
        assertColumn(table, "e", 4, ColumnType.BOOLEAN);
        assertColumn(table, "f", 5, ColumnType.FLOAT);
        assertColumn(table, "g", 6, ColumnType.DOUBLE);
        assertColumn(table, "h", 7, ColumnType.BINARY);
        assertColumn(table, "i", 8, ColumnType.DATE);
        assertColumn(table, "j", 9, ColumnType.LINK);
        assertColumn(table, "k", 10, ColumnType.LINK_LIST);
    }

    public void testAddFieldWithModifiers() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                schema.addClass("Foo")
                        .addString("a", EnumSet.of(RealmModifier.INDEXED))
                        .addLong("b", EnumSet.of(RealmModifier.PRIMARY_KEY))
                        .addBoolean("c", null);
            }
        });

        realm = getDefaultSharedGroup();
        assertTrue(realm.hasTable("class_Foo"));
        Table table = realm.getTable("class_Foo");
        assertEquals(3, table.getColumnCount());
        assertColumn(table, "a", 0, ColumnType.STRING);
        assertColumn(table, "b", 1, ColumnType.INTEGER);
        assertColumn(table, "c", 2, ColumnType.BOOLEAN);
        assertTrue(table.hasIndex(0));
        assertEquals(1, table.getPrimaryKey());
    }

    public void testRemoveEmptyFieldThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.getClass("AllTypes").removeField(null);
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRemoveNonExistingFieldThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.getClass("AllTypes").removeField("unknown");
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRemoveField() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                schema.getClass("AllTypes").removeField("columnString");
            }
        });
        realm = getDefaultSharedGroup();
        Table allTypesTable = realm.getTable("class_AllTypes");
        assertEquals(8, allTypesTable.getColumnCount());
        assertEquals(-1, allTypesTable.getColumnIndex("columnString"));
    }

    public void testRenameEmptyFieldThrows() {
        createEmptyDefaultRealm();

        // From field
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.getClass("AllTypes").renameField(null, "Foo");
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }

        // To field
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.getClass("AllTypes").renameField("columnString", null);
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRenameNonExistingFieldThrows() {
        createEmptyDefaultRealm();
        try {
            Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
                @Override
                public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                    schema.getClass("AllTypes").renameField("foo", "bar");
                }
            });
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRenameField() {
        createEmptyDefaultRealm();
        Realm.migrateRealmAtPath(getDefaultRealmPath(), new RealmMigration() {
            @Override
            public void migrate(RealmSchema schema, long oldVersion, long newVersion) {
                schema.getClass("AllTypes").renameField("columnString", "columnString2");
            }
        });
        realm = getDefaultSharedGroup();
        Table t = realm.getTable("class_AllTypes");
        assertEquals(9, t.getColumnCount());
        assertEquals(-1, t.getColumnIndex("columnString"));
        assertTrue(t.getColumnIndex("columnString2") != -1);
    }

    public void testAddEmptyIndexThrows() {
        fail();
    }

    public void testAddNonExistingIndexThrows() {
        fail();
    }

    public void testAddIllegalIndexThrows() {
        fail();
    }

    public void testAddIndex() {
        fail();
    }

    public void testRemoveIndexEmptyFieldThrows() {
        fail();
    }

    public void testRemoveIndexNonExistingFieldThrows() {
        fail();
    }

    public void testRemoveNonExistingIndexThrows() {
        fail();
    }

    public void testRemoveIndex() {
        fail();
    }

    public void addPrimaryKeyEmptyFieldThrows() {
        fail();
    }

    public void addPrimaryKeyNonExistingFieldThrows() {
        fail();
    }

    public void addPrimaryKey() {
        fail();
    }

    public void testRemoveNonExistingPrimaryKeyThrows() {
        fail();
    }

    public void testRemovePrimaryKey() {
        fail();
    }
}
