/*
 * Copyright 2016 peter.
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
package onl.area51.filesystem.http;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.apache.http.entity.ContentType;
import static org.apache.http.entity.ContentType.*;
import uk.trainwatch.util.MapBuilder;

/**
 * Utility class to handle the resolution of {@link ContentType} based on a file name.
 *
 * @author peter
 */
public class ContentTypeResolver
{

    public static final ContentType PNG = create( "image/png" );
    public static final ContentType JPG = create( "image/jpg" );
    private static final Map<String, ContentType> CONTENT_TYPES;

    static
    {
        CONTENT_TYPES = MapBuilder.<String, ContentType>builder()
                .concurrent()
                .add( ".htm", TEXT_HTML )
                .add( ".html", TEXT_HTML )
                .add( ".jpg", JPG )
                .add( ".jpeg", JPG )
                .add( ".json", APPLICATION_JSON )
                .add( ".log", TEXT_PLAIN )
                .add( ".png", PNG )
                .add( ".text", TEXT_PLAIN )
                .add( ".txt", TEXT_PLAIN )
                .add( ".xml", TEXT_XML )
                .build();
    }

    public static void register( String suffix, ContentType ct )
    {
        Objects.requireNonNull( suffix );
        Objects.requireNonNull( ct );
        String s = suffix.trim().toLowerCase();
        int i = suffix.lastIndexOf( '.' );
        if( i > 0 )
        {
            throw new IllegalArgumentException( "Invalid suffix " + suffix );
        }
        CONTENT_TYPES.putIfAbsent( i == 0 ? s : ("." + s), ct );
    }

    public static ContentType resolve( String path )
    {
        if( path != null && !path.isEmpty() )
        {
            int i = path.lastIndexOf( '.' );
            int j = path.lastIndexOf( '/' );
            if( i > -1 && i > j )
            {
                return CONTENT_TYPES.getOrDefault( path.substring( i ).toLowerCase(), APPLICATION_OCTET_STREAM );
            }
        }
        return ContentType.APPLICATION_OCTET_STREAM;
    }

    public static ContentType resolve( File f )
    {
        return resolve( f == null ? null : f.getName() );
    }

    public static ContentType resolve( Path p )
    {
        return resolve( p == null ? null : p.getName( p.getNameCount() - 1 ).toString() );
    }
}
