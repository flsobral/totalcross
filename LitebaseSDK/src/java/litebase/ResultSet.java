/*********************************************************************************
 *  TotalCross Software Development Kit - Litebase                               *
 *  Copyright (C) 2000-2011 SuperWaba Ltda.                                      *
 *  All Rights Reserved                                                          *
 *                                                                               *
 *  This library and virtual machine is distributed in the hope that it will     *
 *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of    *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         *
 *                                                                               *
 *********************************************************************************/

package litebase;

import totalcross.io.*;
import totalcross.sys.*;
import totalcross.util.*;

/**
 * This class represents a set or rows resulting from a <code>LitebaseConnection.executeQuery()</code> method call.
 * Here's an example:
 *
 * <pre>
 * ResultSet rs = driver.executeQuery(&quot;select name, salary, age from person&quot;);
 * while (rs.next())
 *    Vm.debug(pad(rs.getString(&quot;name&quot;), 32) + pad(rs.getString(&quot;salary&quot;), 16) 
 *                                                     + rs.getInt(&quot;age&quot;) + &quot; years&quot;);
 * </pre>
 *
 * Result sets cannot be constructed directly; instead, you must issue a sql to the driver.
 */
public class ResultSet
{
   /** 
    * Current record position being read. 
    */
   int pos;

   /** 
    * Number of records of the result set. 
    */
   int lastRecordIndex;

   /**
    * When <code>rowsBitmap</code> is generated, indicates what is the boolean relationship between the rows marked in the bitmap and any remaining 
    * WHERE clause.
    */
   int rowsBitmapBoolOp;

   /** 
    * The number of columns in this result set. 
    */
   int columnCount;
   
   /** 
    * The index of the correspodent result set. 
    */
   int indexRs = -1;
   
   /** 
    * Counts the number of indices when running <code>generateIndexedRowsMap()</code>. 
    */
   int indexCount;
   
   // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
   /**
    * The number of valid records of this result set.
    */
   int answerCount = -1;
   
   /** 
    * Indicates if it is a select of the form <code>select * from table</code> or not. 
    */
   boolean isSimpleSelect;
   
   /** 
    * The associated table for the result set. 
    */
   Table table;

   /**
    * A map with rows that satisfy totally or partially the query WHERE clause; generated using the table indices.
    */
   IntVector rowsBitmap;

   /**
    * An auxiliary map with rows that satisfy totally or partially the query WHERE clause; generated from the table indices.
    */
   IntVector auxRowsBitmap; // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.

   /** 
    * The indices used in this result set. 
    */
   IntVector indices = new IntVector(3);

   /**
    * An array with the number of decimal places that is used to format <code>float</code> and <code>double</code> values, when being retrieved using 
    * the <code>getString()</code> method. This can be set at runtime by the user, and it is -1 as default.
    */
   byte[] decimalPlaces; 
   
   /**
    * A map with rows that satisfy totally the query WHERE clause.
    */
   byte[] allRowsBitmap; // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.

   /** 
    * The WHERE clause associated with the result set. 
    */
   SQLBooleanClause whereClause;

   /** 
    * The returned fields of the select used for result set meta data. 
    */
   SQLResultSetField[] fields;

   /** 
    * Contains the hash of the all possible colunm names in the select statement. 
    */
   IntHashtable htName2index;

   /** 
    * The value from the result set that will be read. 
    */
   private SQLValue vrs = new SQLValue();
   
   /**
    * The connection with Litebase.
    */
   LitebaseConnection driver; // juliana@220_3

   /**
    * Returns the meta data for this result set.
    *
    * @return The meta data for this result set.
    * @throws DriverException If the result set or the driver is closed.
    */
   public ResultSetMetaData getResultSetMetaData() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      return new ResultSetMetaData(this);
   }

   /**
    * Closes a result set. Releases all memory allocated for this object. Its a good idea to call this when you no longer needs it, but it is also 
    * called by the GC when the object is no longer in use.
    *
    * @throws DriverException if the result set is closed.
    */
   public void close()
   {
      // juliana@211_4: solved bugs with result set dealing.
      if (table == null) // The result set can't be closed.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RESULTSET_CLOSED));
 
      table = null; // To close the resultSet, just sets its table to null.
   }

   /**
    * Places the cursor before the first record.
    *
    * @throws DriverException If the result set or the driver is closed.
    */
   public void beforeFirst() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      pos = -1;
   }

   /**
    * Places the cursor after the last record.
    *
    * @throws DriverException If the result set or the driver is closed.
    */
   public void afterLast() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      pos = lastRecordIndex + 1;
   }

   /**
    * Places the cursor in the first record of the result set.
    *
    * @return <code>true</code> if it was possible to place the cursor in the first record; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed.
    */
   public boolean first() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      pos = -1; // Sets the position before the first record.
      
      if (next()) // Reads the first record.
         return true;
      
      pos = -1; // guich@105: sets the record to -1 if it can't read the first position.
      return false;
   }

   /**
    * Places the cursor in the last record of the result set.
    *
    * @return <code>true</code> if it was possible to place the cursor in the last record; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed.
    */
   public boolean last() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      pos = lastRecordIndex + 1; // Sets the position after the last record.
      
      if (prev()) // Reads the last record.
         return true;

      pos = -1; // guich@105: sets the record to -1 if it can't read the last position.
      return false;
   }

   /**
    * Gets the next record of the result set.
    *
    * @return <code>true</code> if there is a next record to go to in the result set; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed, or an <code>IOException</code> occurs.
    */
   public boolean next() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      try
      {
         byte[] rowsBitmap = allRowsBitmap;
         Table tableAux = table;
         PlainDB plainDB = tableAux.db;
         DataStreamLE basds = plainDB.basds;
         ByteArrayStream bas = plainDB.bas;
         int last = lastRecordIndex;
         
         // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
         if (rowsBitmap != null)
         {
            int i = pos;
            
            while (i < last)
            {
               i++;
               if ((rowsBitmap[i >> 3] & (1 << (i & 7))) != 0)
               {
                  plainDB.read(pos = i);
                  tableAux.readNullBytesOfRecord(0, false, 0);
                  return true;
               }
            }
            return false;
         }
         
         // juliana@114_10: if it is a simple select, there may be deleted rows, which must be skiped.
         if (tableAux.deletedRowsCount > 0)
         {
            boolean isDeleted = false; // Indicates if it was deleted.
            int lastPos = pos; // juliana@211_4: solved bugs with result set dealing.
   
            // juliana@201_27: solved a bug in next() and prev() that would happen after doing a delete from table_name. 
            while (pos++ < last) 
            {
               plainDB.read(pos); 
               if (!(isDeleted = (basds.readInt() & Utils.ROW_ATTR_MASK) == Utils.ROW_ATTR_DELETED))
                  break;
            }
            
            if (pos <= last && !isDeleted) // Sets the position after the last record.
            {
               bas.setPos(0);
               tableAux.readNullBytesOfRecord(0, false, 0);
               return true;
            }
            
            // juliana@211_4: solved bugs with result set dealing.
            // If there are no more rows to be returned, the last valid one must be used.
            plainDB.read(pos = lastPos);
            bas.setPos(0);
            tableAux.readNullBytesOfRecord(0, false, 0);
            return false;
         }
         
         if (pos < last) // Only returns a row as read if it before the end of the temporary table.
         {
            plainDB.read(++pos);
            tableAux.readNullBytesOfRecord(0, false, 0);
            return true;
         }
      }
      catch (IOException exception)
      {
         throw new DriverException(exception);
      }
      return false;
   }

   /**
    * Returns the previous record of the result set.
    *
    * @return <code>true</code> if there is a previous record to go to in the result set; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed, or an <code>IOException</code> occurs.
    */
   public boolean prev() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      try
      {
         byte[] rowsBitmap = allRowsBitmap;
         Table tableAux = table;
         PlainDB plainDB = tableAux.db;
         DataStreamLE basds = plainDB.basds;
         ByteArrayStream bas = plainDB.bas;
         
         // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
         if (rowsBitmap != null)
         {
            int i = pos;
            
            while (i > 0)
            {
               i--;
               if ((rowsBitmap[i >> 3] & (1 << (i & 7))) != 0)
               {
                  plainDB.read(pos = i);
                  tableAux.readNullBytesOfRecord(0, false, 0);
                  return true;
               }
            }
            return false;
         }
         
         // juliana@114_10: if it is a simple select, there may be deleted rows, which must be skiped.
         if (tableAux.deletedRowsCount > 0)
         {
            boolean isDeleted = false; // Indicates if it was deleted.
            int lastPos = pos; // juliana@211_4: solved bugs with result set dealing.
   
            while (pos-- > 0) // juliana@201_27: solved a bug in next() and prev() that would happen after doing a delete from table_name.  
            {
               plainDB.read(pos);
               if (!(isDeleted = (basds.readInt() & Utils.ROW_ATTR_MASK) == Utils.ROW_ATTR_DELETED))
                  break;
            }
            
            if (pos >= 0 && !isDeleted) // Only returns a row as read if it is not deleted.
            {
               bas.setPos(0);
               tableAux.readNullBytesOfRecord(0, false, 0);
               return true;
            }
            
            // juliana@211_4: solved bugs with result set dealing.
            // If there are no more rows to be returned, the last valid one must be used.
            plainDB.read(pos = lastPos);
            bas.setPos(0);
            tableAux.readNullBytesOfRecord(0, false, 0);
            return false;
         }
   
         if (pos > 0) // Only returns a row as read if it is after the beginning of the temporary table.
         {
            plainDB.read(--pos);
            tableAux.readNullBytesOfRecord(0, false, 0);
            return true;
         }
      }
      catch (IOException exception)
      {
         throw new DriverException(exception);
      }
      return false;
   }

   /**
    * Given the column index (starting from 1), returns a short value that is represented by this column. Note that it is only possible to request 
    * this column as short if it was created with this precision.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public short getShort(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.SHORT)? (short)vrs.asShort : 0;
   }
   
   /**
    * Given the column name (case insensitive), returns a short value that is represented by this column. Note that it is only possible to request 
    * this column as short if it was created with this precision. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public short getShort(String colName)
   {
      return getFromName(colName, SQLElement.SHORT)? (short)vrs.asShort : 0;
   }

   /**
    * Given the column index (starting from 1), returns an integer value that is represented by this column. Note that it is only possible to request 
    * this column as integer if it was created with this precision.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public int getInt(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.INT)? vrs.asInt : 0;
   }

   /**
    * Given the column name (case insensitive), returns an integer value that is represented by this column. Note that it is only possible to request 
    * this column as integer if it was created with this precision. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public int getInt(String colName)
   {
      return getFromName(colName, SQLElement.INT)? vrs.asInt : 0;
   }

   /**
    * Given the column index (starting from 1), returns a long value that is represented by this column. Note that it is only possible to request 
    * this column as long if it was created with this precision.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public long getLong(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.LONG)? vrs.asLong : 0;
   }

   /**
    * Given the column name (case insensitive), returns a long value that is represented by this column. Note that it is only possible to request 
    * this column as long if it was created with this precision. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>.
    */
   public long getLong(String colName) 
   {
      return getFromName(colName, SQLElement.LONG)? vrs.asLong : 0;
   }

   /**
    * Given the column index (starting from 1), returns a float value that is represented by this column. Note that it is only possible to request 
    * this column as float if it was created with this precision.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0.0</code>.
    */
   public double getFloat(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.FLOAT)? vrs.asDouble : 0;
   }

   /**
    * Given the column name (case insensitive), returns a float value that is represented by this column. Note that it is only possible to request t
    * his column as float if it was created with this precision. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0.0</code>.
    */
   public double getFloat(String colName)
   {
      return getFromName(colName, SQLElement.FLOAT)? vrs.asDouble : 0;
   }

   /**
    * Given the column index (starting from 1), returns a double value that is represented by this column. Note that it is only possible to request 
    * this column as double if it was created with this precision.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0.0</code>.
    */
   public double getDouble(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.DOUBLE)? vrs.asDouble : 0;
   }

   /**
    * Given the column name (case insensitive), returns a double value that is represented by this column. Note that it is only possible to request 
    * this column as double if it was created with this precision. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>0.0</code>.
    */
   public double getDouble(String colName)
   {
      return getFromName(colName, SQLElement.DOUBLE)? vrs.asDouble : 0;
   }

   /**
    * Given the column index (starting from 1), returns a char array that is represented by this column. Note that it is only possible to request 
    * this column as a char array if it was created as a string.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    */
   public char[] getChars(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.CHARS)? vrs.asString.toCharArray() : null;
   }

   /**
    * Given the column name (case insensitive), returns a char array that is represented by this column. Note that it is only possible to request 
    * this column as a char array if it was created as a string. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    */
   public char[] getChars(String colName)
   {
      return getFromName(colName, SQLElement.CHARS)? vrs.asString.toCharArray() : null;
   }

   /**
    * Given the column index (starting from 1), returns a byte (blob) array that is represented by this column. Note that it is only possible to 
    * request this column as a blob if it was created this way.
    *
    * @param colIdx the column index.
    * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    */
   public byte[] getBlob(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.BLOB)? vrs.asBlob : null;
   }

   /**
    * Given the column name (case insensitive), returns a byte array (blob) that is represented by this column. Note that it is only possible to 
    * request this column as a blob if it was created this way. This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    */
   public byte[] getBlob(String colName)
   {
      return getFromName(colName, SQLElement.BLOB)? vrs.asBlob : null;
   }

   /**
    * Given the column index (starting from 1), returns a string that is represented by this column. Any column type can be returned as a string. 
    * <code>Double</code>/<code>float</code> values formatting will use the precision set with the <code>setDecimalPlaces()</code> method.
    *
    * @param colIdx The column index.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
    */
   public String getString(int colIdx)
   {
      return getFromIndex(colIdx, SQLElement.UNDEFINED)? vrs.asString : null;
   }

   /**
    * Given the column name (case insensitive), returns a string that is represented by this column. Any column type can be returned as a string. 
    * <code>Double</code>/<code>float</code> values formatting will use the precision set with the <code>setDecimalPlaces()</code> method. This 
    * method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
    */
   public String getString(String colName)
   {
      return getFromName(colName, SQLElement.UNDEFINED)? vrs.asString : null;
   }

   /**
    * Starting from the current cursor position, it reads all result set rows that are being requested. <code>first()</code>, <code>last()</code>, 
    * <code>prev()</code>, or <code>next()</code> must be used to set the current position, but not  <code>beforeFirst()</code> or 
    * <code>afterLast()</code>. It doesn't return BLOB values. <code>null</code> is returned in their places instead.
    *
    * @param count The number of rows to be fetched, or -1 for all. 
    * @return A matrix, where <code>String[0]<code> is the first row, and <code>String[0][0], String[0][1]...</code> are the column elements of the 
    * first row. Returns <code>null</code> if there's no more element to be fetched. Double/float values will be formatted using
    * <code>setDecimalPlaces()</code> settings. If the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    * @throws DriverException If the result set or the driver is closed, or an <code>IOException</code> occurs.
    * @throws SQLParseException If an <code>InvalidDateException</code> occurs.
    */
   public String[][] getStrings(int count) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      if (count < -1) // The number of rows returned can't be negative.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RS_INV_POS));
         
      if (count == -1) // If count = -1, fetch all rows of the result set.
         count = 0xFFFFFFF;

      // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
      Table tableAux = table;
      byte[] rowsBitmap = allRowsBitmap;
      byte[] nulls = tableAux.columnNulls[0];
      byte[] decimals = decimalPlaces;
      SQLResultSetField[] rsFields = fields;
      SQLResultSetField field;
      int init = isSimpleSelect? 1 : 0, // juliana@114_10: skips the rowid.
          rows = 0, // The number of rows to be fetched.
          last = lastRecordIndex,
          
          // juliana@211_3: the string matrix size can't take into consideration rows that are before the result set pointer.
          // The records that are fetched, skipping the deleted rows.
          records = last + 1 - pos, 
          
          i,
          column,
          columns = rowsBitmap == null? columnCount : rsFields.length;
      SQLValue value = vrs;
      int[] offsets = tableAux.columnOffsets;
      int[] types = tableAux.columnTypes;
      String[][] strings = new String[count < records ? count : records][]; // Stores the strings.
      String[] row;
      
      if (count == 0)
         return strings;

      if (pos < 0 || pos > last) // The position of the cursor must be greater then 0 and less then the last position.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RS_INV_POS));
      
      int validRecords = 0; // juliana@211_4: solved bugs with result set dealing.
      
      try
      {
         do
         {  
            row = strings[rows++] = new String[columns]; // juliana@201_19: Does not consider rowid.
            
            i = init - 1;
            while  (++i < columns)
            {
               field = rsFields[i - init];
               if (rowsBitmap == null)
                  column = i;
               else
                  column = field.parameter == null? field.tableColIndex : field.parameter.tableColIndex;
                  
               // Only reads the column if it is not null and not a BLOB.
               if ((nulls[column >> 3] & (1 << (column & 7))) == 0 && types[column] != SQLElement.BLOB)
               {
                  // juliana@220_3
                  tableAux.readValue(value, offsets[column], types[column], decimals == null ? -1 : decimals[column], true, false, false, driver); 
                  
                  // juliana@226_9: strings are not loaded anymore in the temporary table when building result sets. 
                  if (field.isDataTypeFunction)
                     applyDataTypeFunction(field, field.parameter.dataType);
                  
                  row[i - init] = value.asString;
               }
            }
            validRecords++; // juliana@211_4: solved bugs with result set dealing.
         } while (--count > 0 && next()); // Continues until there are rows to be read and the number of rows read is not the desired.
      }
      catch (IOException exception)
      {
         throw new DriverException(exception);
      }
      catch (InvalidDateException exception)
      {
         throw new SQLParseException(exception);
      }
      
      // juliana@211_4: solved bugs with result set dealing.
      // The strings matrix can't have nulls at the end.
      if (strings.length > validRecords)
      {
         String[][] stringsAux = new String[validRecords][];
         Vm.arrayCopy(strings, 0, stringsAux, 0, validRecords);
         return stringsAux;
      }
      
      return strings; // Returns the string matrix.
   }

   /**
    * Starting from the current cursor position, it reads all result set rows of the result set. <code>first()</code>,  <code>last()</code>, 
    * <code>prev()</code>, or <code>next()</code> must be used to set the current position, but not <code>beforeFirst()</code> or 
    * <code>afterLast()</code>. It doesn't return BLOB values. <code>null</code> is returned in their places instead. 
    *
    * @return A matrix, where <code>String[0]<code> is the first row, and <code>String[0][0], String[0][1]...</code> are the column elements of the 
    * first row. Returns <code>null</code> if there's no more element to be fetched. Double/float values will be formatted using the
    * <code>setDecimalPlaces() </code> settings. If the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    */
   public String[][] getStrings()
   {
      return getStrings(-1);
   }

   /**
    * Given the column index (starting from 1), returns a <code>Date</code> value that is represented by this column. Note that it is only possible 
    * to request this column as a date if it was created this way (DATE).
    *
    * @param colIdx The column index.
    * @return The column value as a <code>Date</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    * @throws SQLParseException If an <code>InvalidDateException</code> occurs.
    */
   public Date getDate(int colIdx) throws SQLParseException // rnovais@567_3
   {
      try // Tries to transform a date fetched as an int into a date.
      {
         return getFromIndex(colIdx, SQLElement.DATE)? new Date(vrs.asInt) : null;
      }
      catch (InvalidDateException exception)
      {
         throw new SQLParseException(exception);
      }
   }

   /**
    * Given the column name (case insensitive), returns a <code>Date</code> value that is represented by this column. Note that it is only possible 
    * to request this column as a date if it was created this way (DATE). This method is slightly slower then the method that accepts a column index.
    *
    * @param colName The column name.
    * @return The column value as a <code>Date</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>.
    * @throws SQLParseException If an <code>InvalidDateException</code> occurs.
    */
   public Date getDate(String colName) throws DriverException // rnovais@567_3
   {
      try // Tries to transform a date fetched as an int into a date.
      {
         return getFromName(colName, SQLElement.DATE)? new Date(vrs.asInt): null;
      }
      catch (InvalidDateException exception)
      {
         throw new SQLParseException(exception);
      }
   }

   /**
    * Given the column index (starting from 1), returns a <code>Time</code> (correspondent to a DATETIME data type) value that is represented by this
    * column. Note that it is only possible to request this column as a datetime if it was created this way.
    *
    * @param colIdx The colum index, starting from 1.
    * @return The column value as a <code>Time</code> object; if the DATETIME value is SQL <code>NULL</code>, the value returned is 
    * <code>null</code>.
    */
   public Time getDateTime(int colIdx) // rnovais@567_3
   {
      return getFromIndex(colIdx, SQLElement.DATETIME)? new Time(vrs.asInt, vrs.asShort) : null;
   }

   /**
    * Given the column name (case insensitive), returns a <code>Time</code> (correspondent to a DATETIME data type) value that is represented by this 
    * column. Note that it is only possible to request this column as a datetime if it was created this way. This method is slightly slower then the 
    * method that accepts a column index.
    *
    * @param colName The colum name.
    * @return The column value as a <code>Time</code> object; if the DATETIME value is SQL <code>NULL</code>, the value returned is 
    * <code>null</code>.
    */
   public Time getDateTime(String colName) // rnovais@567_3
   {
      return getFromName(colName, SQLElement.DATETIME) ? new Time(vrs.asInt, vrs.asShort) : null;
   }

   /**
    * Places this result set cursor at the given absolute row. This is the absolute physical row of the table. This method is usually used to restore 
    * the row at a previous row got with the <code>getRow()</code> method.
    *
    * @param row The row to set the cursor.
    * @return <code>true</code> whenever this method does not throw an exception.
    * @throws DriverException the result set or the driver is closed, or an <code>IOException</code> occurs.
    */
   public boolean absolute(int row) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      try // juliana@114_10: if the table of the result set has deleted rows, the absolute row must be searched.
      {
         byte[] rowsBitmap = allRowsBitmap;
         Table tableAux = table;
         PlainDB plainDB = tableAux.db;
         DataStreamLE basds = plainDB.basds;
         ByteArrayStream bas = plainDB.bas;
         int last = lastRecordIndex;
         
         // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
         if (rowsBitmap != null)
         {
            int rowCount = 0;
            
            while (rowCount <= last && rowCount <= row)
            {
               if ((rowsBitmap[rowCount >> 3] & (1 << (rowCount & 7))) == 0)
                  row++;
               rowCount++;
            }
            
            plainDB.read(pos = rowCount - 1);
            bas.setPos(0); // Resets the position of the buffer read.
            tableAux.readNullBytesOfRecord(0, false, 0);
         }
         else if (tableAux.deletedRowsCount > 0)
         {
            int rowCount = 0;
            
            // Continues searching the position until finding the right row or the end of the result set table.
            while (rowCount <= last && rowCount <= row) // juliana@211_4: solved bugs with result set dealing.
            {   
               plainDB.read(rowCount++); // Reads the next row.
   
               // If it was deleted, one more row will be read in total.
               if ((basds.readInt() & Utils.ROW_ATTR_MASK) == Utils.ROW_ATTR_DELETED)
                  row++;
            }
            pos = rowCount - 1;
            bas.setPos(0); // Resets the position of the buffer read.
            tableAux.readNullBytesOfRecord(0, false, 0);
         } 
         
         // guich@300: removed lastRecordIndex + 1. If there are no deleted rows, just reads the row in the right position.
         else if (0 <= row && row <= last)
         {
            plainDB.read(pos = row);
            tableAux.readNullBytesOfRecord(0, false, 0);
         }
      }
      catch (IOException exception)
      {
         throw new DriverException(exception);
      }
      return true;
   }

   /**
    * Moves the cursor <code>rows</code> in distance. The value can be greater or lower than zero.
    *
    * @param rows The distance to move the cursor.
    * @return <code>true</code> whenever this method does not throw an exception.
    * @throws DriverException If the result set or the driver is closed, or an <code>IOException</code> occurs.
    */
   public boolean relative(int rows) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      try // juliana@114_10: if the table of the result set has deleted rows, the relative row must be searched.
      {
         byte[] rowsBitmap = allRowsBitmap;
         Table tableAux = table;
         PlainDB plainDB = tableAux.db;
         ByteArrayStream bas = plainDB.bas;
         int last = lastRecordIndex;

         if (rowsBitmap != null || tableAux.deletedRowsCount > 0) // juliana@211_4: solved bugs with result set dealing.
         {
            // Continues searching the position until finding the right row or the end or the beginning of the result set table.
            if (rows > 0)
               while (--rows >= 0)
                  next();
            else
               while (++rows <= 0)
                  prev();
            if (pos < 0)
               next();
            if (pos > last)
               prev();
               
            bas.setPos(0); // Resets the position of the buffer read.
            tableAux.readNullBytesOfRecord(0, false, 0);
         } 
         else
         {
            // The new pos is pos + rows or 0 (if pos + rows < 0) or bag.lastRecordIndex (if pos + rows > bag.lastRecordIndex).
            int newPos = Math.max(0, Math.min(last, pos + rows));
         
            if (pos != newPos) // If there are no deleted rows, just reads the row in the right position.
            {
               plainDB.read(pos = newPos);
               tableAux.readNullBytesOfRecord(0, false, 0);
            }
         }
      }
      catch (IOException exception)
      {
         throw new DriverException(exception);
      }
      return true;
   }

   /**
    * Returns the current physical row of the table where the cursor is. It must be used with <code>absolute()</code> method.
    *
    * @return The current physical row of the table where the cursor is.
    * @throws DriverException If the result set or the driver is closed.
    */
   public int getRow() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      return pos; // Returns the current position of the cursor.
   }

   /**
    * Sets the number of decimal places that the given column (starting from 1) will have when being converted to <code>String</code>.
    *
    * @param col The column.
    * @param places The number of decimal places.
    * @throws DriverException If the result set or the driver is closed, the column index is invalid, or the value for decimal places is invalid.
    */
   public void setDecimalPlaces(int col, int places) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      // juliana@227_14: corrected a DriverException not being thrown when fetching in some cases when trying to fetch data from an invalid result 
      // set column.
      if (col <= 0 || col > columnCount) // The columns given by the user ranges from 1 to n.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INVALID_COLUMN_NUMBER) + col);
      
      // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
      SQLResultSetField field = fields[col - 1];
      if (isSimpleSelect) // juliana@114_10: skips the rowid.
         col++;
      else if (allRowsBitmap != null)
         col = field.parameter == null? field.tableColIndex + 1 : field.parameter.tableColIndex + 1;
   
      int type = table.columnTypes[--col]; // Gets the column type.
      
      if (places < -1 || places > 40) // Invalid value for decimal places.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RS_DEC_PLACES_START) + places
                                  + LitebaseMessage.getMessage(LitebaseMessage.ERR_RS_DEC_PLACES_END));
      
      if (type == SQLElement.FLOAT || type == SQLElement.DOUBLE) // Only sets the decimal places if the type is FLOAT or DOUBLE.
      {
         if (decimalPlaces == null)
            Convert.fill(decimalPlaces = new byte[columnCount], 0, columnCount, -1);
         decimalPlaces[col] = (byte)places;
      }
      else
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INCOMPATIBLE_TYPES));
   }

   /**
    * Returns the number of rows of the result set.
    *
    * @return The number of rows.
    * @throws DriverException If the result set or the driver is closed.
    */
   public int getRowCount() throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      return allRowsBitmap == null? lastRecordIndex + 1 - table.deletedRowsCount : answerCount; // juliana@114_10: Removes the deleted rows.
   }

   /**
    * Given the column index (starting from 1), indicates if this column has a <code>NULL</code>.
    *
    * @param colIdx The column index.
    * @return <code>true</code> if the value is SQL <code>NULL</code>; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed.
    */
   public boolean isNull(int colIdx) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      // juliana@227_14: corrected a DriverException not being thrown when fetching in some cases when trying to fetch data from an invalid result 
      // set column.
      if (colIdx <= 0 || colIdx > columnCount) // The columns given by the user ranges from 1 to n.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INVALID_COLUMN_NUMBER) + colIdx);
      
      // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
      if (isSimpleSelect) // juliana@114_10: skips the rowid.
         colIdx++;
      else if (allRowsBitmap != null)
      {
         SQLResultSetField field = fields[colIdx - 1];
         colIdx = field.parameter == null? field.tableColIndex + 1 : field.parameter.tableColIndex + 1;
      }

      return (table.columnNulls[0][colIdx - 1 >> 3] & (1 << (colIdx - 1 & 7))) != 0; // Is the column null?
   }

   /**
    * Given the column name (case insensitive), indicates if this column has a <code>NULL</code>.
    *
    * @param colName The column name.
    * @return <code>true</code> if the value is SQL <code>NULL</code>; <code>false</code>, otherwise.
    * @throws DriverException If the result set or the driver is closed, or the column name is not found.
    */
   public boolean isNull(String colName) throws DriverException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      int col = htName2index.get(colName.toLowerCase().hashCode(), -1); // Gets the column.
     
      if (col == -1)  // Tests if the column name is mapped in the result set.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_COLUMN_NOT_FOUND) + colName);

      if (isSimpleSelect) // juliana@114_10: skips the rowid.
         col++;
      else if (allRowsBitmap != null)
      {
         SQLResultSetField field = fields[col];
         col = field.parameter == null? field.tableColIndex : field.parameter.tableColIndex;
      }

      return (table.columnNulls[0][col >> 3] & (1 << (col & 7))) != 0; // Is the column null?
   }

   /**
    * Returns a column value of the result set given its type and column index. DATE and DATETIME values will be returned as a single int or as a 
    * short and an int, respectivelly.
    * 
    * @param colIdx The column index.
    * @param type The type of the column. <code>SQLElement.UNDEFINED</code> must be used to return anything except for blobs as strings.
    * @return <code>true</code> if the column is not null; <code>false</code> otherwise.
    * @throws DriverException If the driver or the driver is closed, an <code>IOException</code>occurs or the kind of return type asked is 
    * incompatible from the column definition type.
    * @throws SQLParseException If an <code>InvalidDateException</code>occurs.
    */
   private boolean getFromIndex(int colIdx, int type) throws DriverException, SQLParseException
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      // juliana@227_14: corrected a DriverException not being thrown when fetching in some cases when trying to fetch data from an invalid result 
      // set column.
      // guich@tc212_6: checks if the index is valid.
      if (colIdx <= 0 || colIdx > columnCount) // The columns given by the user ranges from 1 to n.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INVALID_COLUMN_NUMBER) + colIdx);
      
      // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
      SQLResultSetField field = fields[colIdx - 1];
      if (isSimpleSelect) // juliana@114_10: skips the rowid.
         colIdx++;
      else if (allRowsBitmap != null)
         colIdx = field.parameter == null? field.tableColIndex + 1 : field.parameter.tableColIndex + 1;
         
      // juliana@201_23: the types must be compatible.
      // juliana@227_13: corrected a DriverException not being thrown when issuing ResultSet.getChars() for a column that is not of CHARS, CHARS 
      // NOCASE, VARCHAR, or VARCHAR NOCASE.
      int typeCol = table.columnTypes[colIdx - 1];
      
      if (!(field.isDataTypeFunction && type != SQLElement.UNDEFINED && (typeCol == SQLElement.DATE || typeCol == SQLElement.DATETIME))
       && (typeCol != type && type != SQLElement.UNDEFINED && typeCol != SQLElement.CHARS_NOCASE && typeCol != SQLElement.CHARS))
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INCOMPATIBLE_TYPES));
      if (type == SQLElement.UNDEFINED && typeCol == SQLElement.BLOB) // getString() returns null for blobs.
         vrs.asString = null;
      
      if ((table.columnNulls[0][colIdx - 1 >> 3] & (1 << (colIdx - 1 & 7))) == 0) // Only reads the column if it is not null.
      {
         if (pos < 0 || pos > lastRecordIndex) // The position of the cursor must be greater then 0 and less then the last position.
            throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RS_INV_POS));

         try // Reads and returns the value read.
         {
            // juliana@220_3
            table.readValue(vrs, table.columnOffsets[--colIdx], typeCol, decimalPlaces == null? - 1: decimalPlaces[colIdx], 
                                                                         type == SQLElement.UNDEFINED, false, false, driver);
            
            // juliana@226_9: strings are not loaded anymore in the temporary table when building result sets. 
            if (field.isDataTypeFunction)
               applyDataTypeFunction(field, type);
         }
         catch (IOException exception)
         {
            throw new DriverException(exception);
         }
         catch (InvalidDateException exception)
         {
            throw new SQLParseException(exception);
         }
         return true;
      }
         
      return false;
   }
   
   /**
    * Returns a column value of the result set given its type and column name. DATE and DATETIME values will be returned as a single int or as a 
    * short and an int, respectivelly.
    * 
    * @param colIdx The column name.
    * @param type The type of the column. <code>SQLElement.UNDEFINED</code> must be used to return anything except for blobs as strings.
    * @return <code>true</code> if the column is not null; <code>false</code> otherwise.
    * @throws DriverException If the driver or the driver is closed, an <code>IOException</code>occurs or the kind of return type asked is 
    * incompatible from the column definition type.
    * @throws SQLParseException If an <code>InvalidDateException</code>occurs.
    */
   private boolean getFromName(String colName, int type)
   {
      verifyResultSet(); // The driver or result set can't be closed.
      
      int col = htName2index.get(colName.toLowerCase().hashCode(), -1); // Gets the column index.
      // juliana@227_14: corrected a DriverException not being thrown when fetching in some cases when trying to fetch data from an invalid result 
      // set column.
      if (col == -1) // Tests if the column name is mapped in the result set.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_COLUMN_NOT_FOUND) + colName);
      
      // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
      SQLResultSetField field = fields[col];
      if (isSimpleSelect) // juliana@114_10: skips the rowid.
         col++;
      else if (allRowsBitmap != null)
         col = field.parameter == null? field.tableColIndex : field.parameter.tableColIndex;
            
      // juliana@201_23: the types must be compatible.
      // juliana@227_13: corrected a DriverException not being thrown when issuing ResultSet.getChars() for a column that is not of CHARS, CHARS 
      // NOCASE, VARCHAR, or VARCHAR NOCASE.
      int typeCol = table.columnTypes[col];
     
      if (!(field.isDataTypeFunction && type != SQLElement.UNDEFINED && (typeCol == SQLElement.DATE || typeCol == SQLElement.DATETIME))
       && (typeCol != type && type != SQLElement.UNDEFINED && typeCol != SQLElement.CHARS_NOCASE && typeCol != SQLElement.CHARS))
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_INCOMPATIBLE_TYPES));
      if (type == -1 && typeCol == SQLElement.BLOB) // getString() returns null for blobs.
         vrs.asString = null;
      
      if ((table.columnNulls[0][col >> 3] & (1 << (col & 7))) == 0) // Only reads the column if it is not null.
      {

         try // Reads and returns the value read.
         {
            // juliana@220_3
            table.readValue(vrs, table.columnOffsets[col], typeCol, decimalPlaces == null? - 1: decimalPlaces[col], 
                                                                    type == SQLElement.UNDEFINED, false, false, driver);
            
            // juliana@226_9: strings are not loaded anymore in the temporary table when building result sets. 
            if (field.isDataTypeFunction)
               applyDataTypeFunction(field, type);
         }
         catch (IOException exception)
         {
            throw new DriverException(exception);
         }
         catch (InvalidDateException exception)
         {
            throw new SQLParseException(exception);
         }
         return true;
      }
         
      return false;
   }
   
   /**
    * Gets the value of a column of the underlying table used by the result set.
    *
    * @param col The number of the column from which the value will be fetched.
    * @param val The object where the value will be stored.
    * @return The value read.
    * @throws IOExeption If an internal method throws it.
    * @throws InvalidDateException If an internal method throws it.
    */
   SQLValue sqlwhereclausetreeGetTableColValue(int col, SQLValue val) throws IOException, InvalidDateException
   {
      // juliana@220_3
      table.readValue(val, table.columnOffsets[col], table.columnTypes[col], -1, false, (table.columnNulls[0][col >> 3] & (1 << (col & 7))) != 0, 
                                                                                         true, driver);
      return val;
   }

   /**
    * Gets the next record of a result set. This method is to be used by the result sets created internally by the Litebase code, not by external 
    * result sets.
    * 
    * @return <code>true</code> if there is a next record to go to in the result set; <code>false</code>, otherwise.
    * @throws IOExeption If an internal method throws it.
    * @throws InvalidDateException If an internal method throws it.
    * @throws InvalidNumberException If an internal method throws it.
    */
   boolean getNextRecord() throws IOException, InvalidDateException, InvalidNumberException
   {
      PlainDB db = table.db;
      
      if (rowsBitmap != null) // Desired rows partially computed using the indexes?
      {
         int p;
         int[] items = rowsBitmap.items;
         if (pos < lastRecordIndex)
         {
            if (whereClause == null)
            {
               // juliana@227_7: solved a bug on delete when trying to delete a key from a column which has index and there are deleted rows with the 
               // same key.
               // No WHERE clause. Just returns the rows marked in the bitmap.
               while ((p = Utils.findNextBitSet(items, pos + 1)) != -1 && p <= lastRecordIndex)
               {
                  db.read(pos = p);
                  if (db.recordNotDeleted())
                     return true;
               }
            }
            else
               // With a remaining WHERE clause there are 2 situations.
               // 1) The relationship between the bitmap and the WHERE clause is an AND relationship, and
               // 2) The relationship between the bitmap and the WHERE clause is an OR relationship.
               if (rowsBitmapBoolOp == SQLElement.OP_BOOLEAN_AND)
                  // AND case - walks through the bits that are set in the bitmap and checks if rows satisfies the where clause.
                  while ((p = Utils.findNextBitSet(items, pos + 1)) != -1 && p <= lastRecordIndex)
                  {
                     db.read(pos = p);
                     
                     // juliana@227_7: solved a bug on delete when trying to delete a key from a column which has index and there are deleted rows 
                     // with the same key.
                     if (db.recordNotDeleted() && whereClause.sqlBooleanClauseSatisfied(this))
                        return true;
                  }
               else
                  // OR case - walks through all records. If the corresponding bit is set in the bitmap, do not need to evaluate WHERE clause.
                  // Otherwise, checks if the row satisifies the WHERE clause.
                  // juliana@201_27: solved a bug in next() and prev() that would happen after doing a delete from table_name. 
                  while (pos++ < lastRecordIndex) 
                  {
                     db.read(pos);
                     if (rowsBitmap.isBitSet(pos) || (db.recordNotDeleted() && whereClause.sqlBooleanClauseSatisfied(this)))
                        return true;
                  }
                  
         }
         return false;
      }
      else
         // If the where clause exists, it needs to be satisfied.
         while (pos++ < lastRecordIndex) // juliana@201_27: solved a bug in next() and prev() that would happen after doing a delete from table_name. 
         {
            db.read(pos);
            if (db.recordNotDeleted() && (whereClause == null || whereClause.sqlBooleanClauseSatisfied(this)))
               return true;
         }
            
      return false;
   }
   
   // juliana@230_14: removed temporary tables when there is no join, group by, order by, and aggregation.
   /**
    * Applies a function when fetching data from the result set.
    * 
    * @param field The field where the function is being applied.
    * @param type The type of the field being returned.
    */
   private void applyDataTypeFunction(SQLResultSetField field, int type)
   {
      vrs.applyDataTypeFunction(field.sqlFunction, field.parameter.dataType);
      if (type == SQLElement.UNDEFINED)
         switch (field.sqlFunction)
         {
            case SQLElement.FUNCTION_DT_YEAR:      
            case SQLElement.FUNCTION_DT_MONTH:  
            case SQLElement.FUNCTION_DT_DAY:    
            case SQLElement.FUNCTION_DT_HOUR:   
            case SQLElement.FUNCTION_DT_MINUTE: 
            case SQLElement.FUNCTION_DT_SECOND: 
            case SQLElement.FUNCTION_DT_MILLIS: 
               vrs.asString = Convert.toString(vrs.asShort);
               break;
            case SQLElement.FUNCTION_DT_ABS:
               switch (field.parameter.dataType)
               {
                  case SQLElement.SHORT:
                     vrs.asString = Convert.toString(vrs.asShort);
                     break;
                  case SQLElement.INT:
                     vrs.asString = Convert.toString(vrs.asInt);
                     break;
                  case SQLElement.LONG:
                     vrs.asString = Convert.toString(vrs.asLong);
                     break;
                  case SQLElement.FLOAT:
                  case SQLElement.DOUBLE:
                     vrs.asString = Convert.toString(vrs.asDouble);
                     break;
               }
         }
   }
   
   /**
    * Verifies if the result set and its driver are openned.
    * 
    * @throws DriverException if the result set or its driver is closed.
    */
   void verifyResultSet()
   {
      // juliana@211_4: solved bugs with result set dealing.
      if (table == null) // The ResultSet can't be closed.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_RESULTSET_CLOSED));
      if (driver.htTables == null) // juliana@227_4: the connection where the result set was created can't be closed while using it.
         throw new DriverException(LitebaseMessage.getMessage(LitebaseMessage.ERR_DRIVER_CLOSED));
   }
}
