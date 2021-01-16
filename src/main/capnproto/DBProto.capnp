# The protocol for the DB server/client

# File ID, generated by `capnp id`
@0xc1d7ab2934a7a0e5;

using Java = import "/capnp/java.capnp";
$Java.package("dev.twelveoclock.supernovae.proto");
$Java.outerClassname("DBProto");

# Protocol level

struct Message @0x9c63d8afbc958760 {
    union {
        createDb @0 :CreateDB;
        deleteDb @1 :DeleteDB;
        createTable @2 :CreateTable;
        selectDb @3 :SelectDB;
        selectAllRows @4 :SelectAllRows;
        selectRows @5 :SelectRows;
        deleteRows @6 :DeleteRows;
        insertRow @7 :InsertRow;
        updateRows @8 :UpdateRows;
        selectRowResponse @9 :SelectRowResponse;
        selectTableResponse @10 :SelectTableResponse;
        cacheRows @11 :CacheRows;
        cacheTable @12 :CacheTable;
        uncacheRows @13 :UncacheRows;
        uncacheTable @14 :UncacheTable;
        deleteTable @15 :DeleteTable;
        selectTable @16 :SelectTable;
        clearTable @17 :ClearTable;
        listenToTable @18 :ListenToTable;
        removeListenToTable @19 :RemoveListenToTable;
        removeAllListenToTables @20 :RemoveAllListenToTables;
        updateNotification @21 :UpdateNotification;
        blob @22 : Blob;
    }
}

struct CreateDB @0x881da94cf2bcd66a {
    databaseName @0 :Text;
}

struct DeleteDB @0xc9e517034562b447 {
    databaseName @0 :Text;
}

struct CreateTable @0xb5e29811110d0f61 {
    tableName @0 :Text;
    keyColumn @1 :Text;
    shouldCacheAll @2 :Bool;
}

struct SelectDB @0xed4c20264edeecb5 {
    databaseName @0 :Text;
}

struct SelectAllRows @0xd7a213ee08c1ac9b {
    tableName @0 :Text;
    onlyInCache @1 :Bool = false;
}

struct SelectRows @0xffa903674167c9f3 {
    tableName @0 :Text;
    filters @1 :List(Filter);
    onlyCheckCache @2 :Bool = false;
    loadIntoCache @3 :Bool = false;
    amountOfRows @4 :UInt32 = 0; # 0 represents all
}

struct DeleteRows @0xa8bf202a88766929 {
    tableName @0 :Text;
    filters @1 :List(Filter);
    amountOfRows @2 :UInt32 = 0; # 0 represents all
    onlyCheckCache @3 :Bool = false;
}

struct InsertRow @0xbf1e316e1ed353d5 {
    tableName @0 :Text;
    row @1 :Text; # Json value
    shouldCache @2 :Bool = false;
}

struct UpdateRows @0xeb442c65499a092b {
    tableName @0 :Text;
    filter @1 :Filter;
    columnName @2 :Text;
    value @3 :Text; # Json value
    amountOfRows @5 :UInt32 = 0; # 0 represents all
    onlyCheckCache @4 :Bool = false;
}

# This will be sent N amount of times
struct SelectRowResponse @0x97678ad2cfb46cb0 {
    # Rows will be encoded in Json
    # Maybe move to a compressed format in the future
    row @0 :Text;
}

struct SelectTableResponse @0x9e0fc6cba7b04f47 {
    tableName @0 :Text;
    keyColumn @1 :Text;
    shouldCacheAll @2 :Bool;
}

# Select a table into cache
struct SelectTable @0x99040191d9151dc0 {
    tableName @0 :Text;
}


# Loads a table into cache
struct CacheTable @0xad23a3c5ab56508e {
    tableName @0 :Text;
}

struct CacheRows @0x929b0ace4e99148f {
    tableName @0 :Text;
    filter @1 :Filter;
    #onlyCheckCache @2 :Bool = false;
}


struct UncacheRows @0xcd5554086acdb7c9 {
    tableName @0 :Text;
    filter @1 :Filter;
    onlyCheckCache @2 :Bool = false;
}

struct UncacheTable @0x99996f3faa723c37 {
    tableName @0 :Text;
}

struct DeleteTable @0x9f81cc03d24bfe84 {
    tableName @0 :Text;
}

struct ClearTable @0xbb74f5c9a2265637 {
    tableName @0 :Text;
}

struct ListenToTable @0xd2765b6506a1cda1 {
    tableName @0 :Text;
}

struct RemoveListenToTable @0xee62268848fe97ab {
    tableName @0 :Text;
}

struct RemoveAllListenToTables @0x985a9a3fb1bbd47c {

}


struct UpdateNotification @0xa549209cca18d206 {
    tableName @0 :Text;
    type @1 :UpdateType;
    row @2 :Text; # Json value
}


struct Blob @0xf7a7e4dd9ac95c29 {
    list @0 :List(Message);
}


# API level

struct Filter @0xeafb36c661f158a1 {
    columnName @0 :Text;
    check @1 :Check;
    compareToValue @2 :Text;
}

enum Check @0xd1e179ce4037146c {
    equal @0;
    lesserThan @1;
    greaterThan @2;
    lesserThanOrEqual @3;
    greaterThanOrEqual @4;
}

enum UpdateType @0xc008e5c7232f6847 {
    modification @0;
    insert @1;
    deletion @2;
}