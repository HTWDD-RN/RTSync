package de.dmos.rtsync.streaming;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.OptionalInt;

import se.l4.exobytes.internal.streaming.JsonInput;
import se.l4.exobytes.streaming.AbstractStreamingInput;
import se.l4.exobytes.streaming.Token;
import se.l4.exobytes.streaming.ValueConversion;

/**
 * A copy of {@link JsonInput} which doesn't cast read doubles into longs in readDynamic0. The copy was needed because
 * of all the private methods.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class CustomJsonInput extends AbstractStreamingInput
{
  private static final char NULL   = 0;
  private static final int  LEVELS = 20;

  private final Reader      in;

  private final char[]      buffer;
  private int               position;
  private int               limit;
  private int               level;

  private boolean[]         lists;

  public CustomJsonInput(InputStream in) throws IOException
  {
    this(new InputStreamReader(in, StandardCharsets.UTF_8));
  }

  public CustomJsonInput(Reader in) throws IOException
  {
    this.in = in;

    lists = new boolean[LEVELS];
    buffer = new char[1024];

    lists[0] = true;
  }

  @Override
  public void close() throws IOException
  {
    in.close();
  }

  @Override
  protected IOException raiseException(String message)
  {
    return new IOException(message);
  }

  @Override
  protected Token peek0() throws IOException
  {
    readWhitespace();

    if ( limit - position < 1 )
    {
      if ( false == readAhead(1) )
      {
        return Token.END_OF_STREAM;
      }
    }

    if ( limit - position > 0 )
    {
      return toToken(buffer[position]);
    }

    return Token.END_OF_STREAM;
  }

  @Override
  public Token next0() throws IOException
  {
    Token token = peek();
    switch (token)
    {
      case OBJECT_END:
      case LIST_END:
      {
        readNext();

        char c = peekChar();
        if ( c == ',' )
        {
          read();
        }

        level--;
        return token;
      }
      case OBJECT_START:
      case LIST_START:
        readNext();
        increaseLevel(token == Token.LIST_START);
        return token;
      case NULL:
      {
        String v = readNonString();
        if ( !"null".equals(v) )
        {
          throw raiseException("Expected null, but encountered malformed null-value: " + v);
        }

        markValueRead();
        return token;
      }
      default:
        return token;
    }
  }

  private void increaseLevel(boolean isList)
  {
    level++;
    if ( lists.length == level )
    {
      // Grow lists when needed
      lists = Arrays.copyOf(lists, level * 2);
    }

    lists[level] = isList;
  }

  @Override
  public OptionalInt getLength()
  {
    return OptionalInt.empty();
  }

  @Override
  protected void skipValue() throws IOException
  {
    char c = peekChar();
    if ( c == '"' )
    {
      // This is a string
      readString(true);

      if ( peekChar() == ':' )
      {
        char next = readNext();
        if ( next != ':' )
        {
          throw raiseException("Expected `:`, got `" + next + "`");
        }
      }
    }
    else
    {
      _outer: while (true)
      {
        c = peekChar(false);
        switch (c)
        {
          case NULL:
          case '}':
          case ']':
          case ',':
          case ':':
            break _outer;
          default:
            if ( Character.isWhitespace(c) )
            {
              break _outer;
            }
        }

        read();
      }
    }

    markValueRead();
  }

  private String readNonString() throws IOException
  {
    StringBuilder value = new StringBuilder();
    _outer: while (true)
    {
      char c = peekChar(false);
      switch (c)
      {
        case NULL:
        case '}':
        case ']':
        case ',':
        case ':':
          break _outer;
        default:
          if ( Character.isWhitespace(c) )
          {
            break _outer;
          }
      }

      value.append(read());
    }

    return value.toString();
  }

  @Override
  protected Object readDynamic0() throws IOException
  {
    if ( current() == Token.NULL )
    {
      return null;
    }
    else if ( peekChar() == '"' )
    {
      return readString();
    }
    else
    {
      String v = readNonString();
      markValueRead();
      switch (v)
      {
        case "null":
          return null;
        case "false":
          return false;
        case "true":
          return true;
        default:
          try
          {
            return Long.parseLong(v);
          }
          catch (NumberFormatException e)
          {
            try
            {
              return Double.parseDouble(v);
            }
            catch (NumberFormatException e2)
            {
              throw raiseException("Unable to read dynamic value, was: " + v);
            }
          }
      }
    }
  }

  @Override
  public boolean readBoolean() throws IOException
  {
    String value = readNonString();
    switch (value)
    {
      case "true":
        markValueRead();
        return true;
      case "false":
        markValueRead();
        return false;
    }

    throw raiseException("Expected boolean but found " + value);
  }

  @Override
  public byte readByte() throws IOException
  {
    return ValueConversion.toByte(readInt());
  }

  @Override
  public char readChar() throws IOException
  {
    if ( peekChar() == '"' )
    {
      String s = readString();
      if ( s.length() != 1 )
      {
        throw raiseException("Expected single character but string value was not a single character");
      }

      return s.charAt(0);
    }
    else
    {
      return ValueConversion.toChar(readInt());
    }
  }

  @Override
  public short readShort() throws IOException
  {
    return ValueConversion.toShort(readInt());
  }

  @Override
  public int readInt() throws IOException
  {
    return ValueConversion.toInt(readLong());
  }

  @Override
  public long readLong() throws IOException
  {
    String in = readNonString();
    markValueRead();

    try
    {
      return Long.parseLong(in);
    }
    catch (NumberFormatException e)
    {
      try
      {
        return (long) Double.parseDouble(in);
      }
      catch (NumberFormatException e2)
      {
        throw raiseException("Unable to read number: " + in);
      }
    }
  }

  @Override
  public float readFloat() throws IOException
  {
    return (float) readDouble();
  }

  @Override
  public double readDouble() throws IOException
  {
    String in = readNonString();
    markValueRead();

    try
    {
      return Double.parseDouble(in);
    }
    catch (NumberFormatException e)
    {
      throw raiseException("Unable to read number: " + in);
    }
  }

  @Override
  public String readString() throws IOException
  {
    String s = readString(true);

    if ( peekChar() == ':' )
    {
      readNext();
    }

    markValueRead();
    return s;
  }

  @Override
  public byte[] readByteArray() throws IOException
  {
    String value = readString();
    return Base64.getDecoder().decode(value);
  }

  @Override
  public InputStream readByteStream() throws IOException
  {
    return new ByteArrayInputStream(readByteArray());
  }

  private String readString(boolean readStart) throws IOException
  {
    StringBuilder key = new StringBuilder();
    char c = read();
    if ( readStart )
    {
      if ( c != '"' )
      {
        throw raiseException("Expected \", but got " + c);
      }
      c = read();
    }

    while (c != '"')
    {
      if ( c == '\\' )
      {
        readEscaped(key);
      }
      else
      {
        key.append(c);
      }

      c = read();
    }

    return key.toString();
  }

  private void readEscaped(StringBuilder result) throws IOException
  {
    char c = read();
    switch (c)
    {
      case '\'':
        result.append('\'');
        break;
      case '"':
        result.append('"');
        break;
      case '\\':
        result.append('\\');
        break;
      case '/':
        result.append('/');
        break;
      case 'r':
        result.append('\r');
        break;
      case 'n':
        result.append('\n');
        break;
      case 't':
        result.append('\t');
        break;
      case 'b':
        result.append('\b');
        break;
      case 'f':
        result.append('\f');
        break;
      case 'u':
        // Unicode, read 4 chars and treat as hex
        readAhead(4);
        String s = new String(buffer, position, 4);
        result.append((char) Integer.parseInt(s, 16));
        position += 4;
        break;
    }
  }

  private char peekChar() throws IOException
  {
    return peekChar(true);
  }

  private char peekChar(boolean ws) throws IOException
  {
    if ( ws )
    {
      readWhitespace();
    }

    if ( limit - position < 1 )
    {
      if ( false == readAhead(1) )
      {
        return NULL;
      }
    }

    if ( limit - position > 0 )
    {
      return buffer[position];
    }

    return NULL;
  }

  @Override
  protected void markValueRead() throws IOException
  {
    super.markValueRead();

    // Check for trailing commas
    readWhitespace();

    char c = peekChar();
    if ( c == ',' )
    {
      read();
    }
  }

  /**
   * Read all of the whitespace at the current position.
   *
   * @throws IOException
   */
  private void readWhitespace() throws IOException
  {
    if ( limit - position > 0 && !Character.isWhitespace(buffer[position]) )
    {
      return;
    }

    while (true)
    {
      if ( limit - position < 1 )
      {
        if ( !readAhead(1) )
        {
          return;
        }
      }

      char c = buffer[position];
      if ( Character.isWhitespace(c) || c == ',' )
      {
        position++;
      }
      else
      {
        return;
      }
    }
  }

  /**
   * Reader the next character while also skipping whitespace as necessary.
   *
   * @return
   * @throws IOException
   */
  private char readNext() throws IOException
  {
    readWhitespace();

    return read();
  }

  /**
   * Read a single character at the current position.
   *
   * @return
   * @throws IOException
   */
  private char read() throws IOException
  {
    if ( limit - position < 1 )
    {
      if ( !readAhead(1) )
      {
        throw new EOFException();
      }
    }

    return buffer[position++];
  }

  /**
   * Perform a read ahead for the given number of characters. Will read the characters into the buffer.
   *
   * @param minChars
   * @return
   * @throws IOException
   */
  private boolean readAhead(int minChars) throws IOException
  {
    if ( limit < 0 )
    {
      return false;
    }
    else if ( position + minChars < limit )
    {
      return true;
    }
    else if ( limit >= position )
    {
      // If we have characters left we need to keep them in the buffer
      int stop = limit - position;

      System.arraycopy(buffer, position, buffer, 0, stop);

      limit = stop;
    }
    else
    {
      limit = 0;
    }

    int read = read(buffer, limit, buffer.length - limit);

    position = 0;
    limit += read;

    if ( read == 0 )
    {
      return false;
    }

    if ( read < minChars )
    {
      throw raiseException("Needed " + minChars + " but got " + read);
    }

    return true;
  }

  /**
   * Fully read a number of characters.
   *
   * @param buffer
   * @param offset
   * @param length
   * @return
   * @throws IOException
   */
  private int read(char[] buffer, int offset, int length) throws IOException
  {
    int result = 0;
    while (result < length)
    {
      int l = in.read(buffer, offset + result, length - result);
      if ( l == -1 )
      {
        break;
      }
      result += l;
    }

    return result;
  }

  /**
   * Take the current character and turn it into a {@link Token}.
   *
   * @param c
   * @return
   */
  private Token toToken(char c) throws IOException
  {
    if ( c == NULL )
    {
      return Token.END_OF_STREAM;
    }

    switch (c)
    {
      case '{':
        return Token.OBJECT_START;
      case '}':
        return Token.OBJECT_END;
      case '[':
        return Token.LIST_START;
      case ']':
        return Token.LIST_END;
      case '"':
        return Token.VALUE;
      case 'n':
        return Token.NULL;
      case 'f':
      case 't':
      case '+':
      case '-':
        return Token.VALUE;
      default:
        if ( c >= '0' && c <= '9' )
        {
          return Token.VALUE;
        }
    }

    throw raiseException("Unexpected JSON input, next character is: " + c);
  }
}
