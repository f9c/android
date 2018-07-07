package com.github.f9c.android.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.f9c.android.message.Message
import com.github.f9c.android.contact.Contact
import com.github.f9c.client.datamessage.TextMessage


class DbHelper(context: Context) : SQLiteOpenHelper(context, "f9c", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE CONTACTS (publicKey TEXT primary key, alias TEXT unique, statusText Text, server TEXT, profileIcon BLOB)")
        db.execSQL("CREATE TABLE MESSAGES (contactRowId INTEGER, sendDate INT, receiveDate INT, message TEXT, read INTEGER, incoming INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE CONTACTS add server TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE CONTACTS add statusText TEXT")
        }
    }

    fun contactExistsForAlias(alias: String): Boolean {
        return evaluateCount("SELECT COUNT(*) FROM CONTACTS WHERE alias = ?", arrayOf(alias))
    }

    fun contactExistsForPublicKey(publicKey: String): Boolean {
        return evaluateCount("SELECT COUNT(*) FROM CONTACTS WHERE publicKey = ?", arrayOf(publicKey))
    }

    private fun evaluateCount(countQuery: String, selectionArgs: Array<String>): Boolean {
        val c = readableDatabase.rawQuery(countQuery, selectionArgs)

        c.use { c ->
            return c.moveToFirst() && c.getInt(0) > 0
        }
    }

    fun insertContact(alias: String, server: String, publicKey: String) {
        val contentValues = ContentValues()
        contentValues.put("alias", alias)
        contentValues.put("server", server)
        contentValues.put("publicKey", publicKey)
        writableDatabase.insert("CONTACTS", null, contentValues)
    }

    fun updateContact(publicKey: String, alias: String, statusText: String?, profileImage: ByteArray?) {
        val contentValues = ContentValues()
        contentValues.put("alias", alias)
        contentValues.put("profileIcon", profileImage)
        contentValues.put("statusText", statusText)
        writableDatabase.update("CONTACTS", contentValues, "publicKey = ?", arrayOf(publicKey))

    }

    fun loadContacts(): MutableList<Contact> {

        val c = readableDatabase.rawQuery("SELECT rowId, publicKey, server, alias, statusText, profileIcon from CONTACTS order by alias", arrayOf())

        val result = mutableListOf<Contact>()

        while (c.moveToNext()) {
            result.add(contactFromRow(c))
        }
        c.close()
        return result
    }

    private fun toImage(profileIconBytes: ByteArray?): Bitmap? {
        if (profileIconBytes != null) {
            return BitmapFactory.decodeByteArray(profileIconBytes, 0, profileIconBytes.size)
        }
        return null
    }

    fun insertMessage(textMessage: TextMessage) {
        val publicKeyString = RsaKeyToStringConverter.encodePublicKey(textMessage.senderPublicKey)

        val rowId = loadContactRowId(publicKeyString)
        val contentValues = ContentValues()
        contentValues.put("contactRowId", rowId)
        contentValues.put("sendDate", textMessage.timestamp)
        contentValues.put("receiveDate", System.currentTimeMillis())
        contentValues.put("message", textMessage.msg)
        contentValues.put("read", 0)
        contentValues.put("incoming", 1)
        writableDatabase.insert("MESSAGES", null, contentValues)
    }

    fun insertSentMessage(contact: Contact, msg: String) {
        val contentValues = ContentValues()
        contentValues.put("contactRowId", contact.rowId)
        contentValues.put("sendDate", System.currentTimeMillis())
        contentValues.put("receiveDate", System.currentTimeMillis())
        contentValues.put("message", msg)
        contentValues.put("read", 1)
        contentValues.put("incoming", 0)
        writableDatabase.insert("MESSAGES", null, contentValues)
    }


    fun loadContactRowId(publicKeyString: String): Int {
        val c = readableDatabase.rawQuery("SELECT rowId from CONTACTS WHERE publicKey=?", arrayOf(publicKeyString))
        c.use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    fun loadContactByPublicKey(publicKey: String): Contact {
        val c = readableDatabase.rawQuery("SELECT rowId, publicKey, server, alias, statusText, profileIcon from CONTACTS where publicKey = ?", arrayOf(publicKey))
        c.use { c ->
            c.moveToFirst()
            return contactFromRow(c)
        }
    }

    fun loadContactRowIdForAlias(alias: String): Int {
        val c = readableDatabase.rawQuery("SELECT rowId from CONTACTS where alias = ?", arrayOf(alias))
        c.use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }



    fun loadContact(contactId: String): Contact {
        val c = readableDatabase.rawQuery("SELECT rowId, publicKey, server, alias, statusText, profileIcon from CONTACTS where rowId = ?", arrayOf(contactId))
        c.use { c ->
            c.moveToFirst()
            return contactFromRow(c)
        }
    }

    fun removeContact(contact: Contact) {
        writableDatabase.delete("MESSAGES", "contactRowId = ?", arrayOf(contact.rowId.toString()))
        writableDatabase.delete("CONTACTS", "rowId = ?", arrayOf(contact.rowId.toString()))
    }

    private fun contactFromRow(c: Cursor) =
            Contact(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), toImage(c.getBlob(5)))

    fun loadMessages(contactId: Int): MutableList<Message> {

        val c = readableDatabase.rawQuery("SELECT message, sendDate, incoming  FROM MESSAGES WHERE contactRowId = ? order by receiveDate asc", arrayOf(contactId.toString()))

        val result = mutableListOf<Message>()

        while (c.moveToNext()) {
            result.add(Message(c.getString(0), c.getLong(1), c.getInt(2) == 1))
        }
        c.close()
        return result
    }

    fun loadUnreadMessages(): MutableMap<String, MutableList<String>> {
        val c = readableDatabase.rawQuery("SELECT C.alias, M.message from CONTACTS C, MESSAGES M where M.read = 0 AND C.rowId = M.contactRowId order by C.alias, M.receiveDate", arrayOf())

        val result = mutableMapOf<String, MutableList<String>>()

        while (c.moveToNext()) {
            val alias = c.getString(0)
            val message = c.getString(1)

            if (!result.containsKey(alias)) {
                result[alias] = mutableListOf()
            }
            result[alias]!!.add(message)
        }
        c.close()
        return result
    }

    fun markMessagesRead(contactId: String) {
        val contentValues = ContentValues()
        contentValues.put("read", 1)
        writableDatabase.update("MESSAGES", contentValues, "contactRowId = ? and read = 0", arrayOf(contactId))
    }


}