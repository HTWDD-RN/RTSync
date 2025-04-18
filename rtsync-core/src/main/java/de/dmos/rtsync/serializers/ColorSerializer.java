package de.dmos.rtsync.serializers;

import java.awt.Color;
import java.io.IOException;

import se.l4.exobytes.Serializer;
import se.l4.exobytes.standard.StringSerializer;
import se.l4.exobytes.streaming.StreamingInput;
import se.l4.exobytes.streaming.StreamingOutput;

public class ColorSerializer implements Serializer<Color>
{
  private final StringSerializer _stringSerializer;
  private final boolean          _prependSharp;

  public ColorSerializer()
  {
    this(false);
  }

  public ColorSerializer(boolean prependSharp)
  {
    _stringSerializer = MessageSerialization.STRING_SERIALIZER;
    _prependSharp = prependSharp;
  }

  @Override
  public Color read(StreamingInput in) throws IOException
  {
    String string = _stringSerializer.read(in);
    try
    {
      return parseColor(string);
    }
    catch (NumberFormatException nfe)
    {
      throw new IOException(nfe);
    }
  }

  @Override
  public void write(Color object, StreamingOutput out) throws IOException
  {
    _stringSerializer.write(colorToString(object), out);
  }

  public String colorToString(Color color)
  {
    if ( color == null )
    {
      return null;
    }
    String colorString = Integer.toHexString(color.getRGB()).substring(2);
    return _prependSharp ? "#" + colorString : colorString;
  }

  public Color parseColor(String colorString) throws NumberFormatException
  {
    if ( colorString == null || colorString.isBlank() )
    {
      return null;
    }
    colorString = colorString.charAt(0) == '#' ? colorString.substring(1) : colorString;
    return new Color(Integer.parseUnsignedInt(colorString, 16));
  }

  public Color tryParseColor(String colorString)
  {
    try
    {
      return parseColor(colorString);
    }
    catch (NumberFormatException nfe)
    {
      return null;
    }
  }
}
