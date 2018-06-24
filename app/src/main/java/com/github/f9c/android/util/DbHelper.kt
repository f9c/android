package com.github.f9c.android.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.f9c.android.chat.Message
import com.github.f9c.android.contacts.Contact
import com.github.f9c.client.datamessage.TextMessage


class DbHelper(context: Context) : SQLiteOpenHelper(context, "f9c", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE CONTACTS (publicKey TEXT primary key, alias TEXT unique, server TEXT, profileIcon BLOB)")
        db.execSQL("CREATE TABLE MESSAGES (contactRowId INTEGER, sendDate INT, receiveDate INT, message TEXT, read INTEGER, incoming INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE CONTACTS add server TEXT")
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

    fun loadContacts(): MutableList<Contact> {

        val c = readableDatabase.rawQuery("SELECT rowId, publicKey, alias, profileIcon from CONTACTS order by alias", arrayOf())

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


    private fun loadContactRowId(publicKeyString: String): Int {
        val c = readableDatabase.rawQuery("SELECT rowId from CONTACTS WHERE publicKey=?", arrayOf(publicKeyString))
        c.use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    fun loadContact(contactId: String): Contact {
        val c = readableDatabase.rawQuery("SELECT rowId, publicKey, alias, profileIcon from CONTACTS where rowId = ?", arrayOf(contactId))
        c.use { c ->
            c.moveToFirst()
            return contactFromRow(c)
        }
    }

    private fun contactFromRow(c: Cursor) =
            Contact(c.getInt(0), c.getString(1), c.getString(2), toImage(c.getBlob(3)))

    fun loadMessages(contactId: Int): MutableList<Message> {

        val c = readableDatabase.rawQuery("SELECT message, incoming  FROM MESSAGES WHERE contactRowId = ? order by receiveDate asc", arrayOf(contactId.toString()))

        val result = mutableListOf<Message>()

        while (c.moveToNext()) {
            result.add(Message(c.getString(0), c.getInt(1) == 1))
        }
        c.close()
        return result
    }


}