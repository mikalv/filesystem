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
package onl.area51.filesystem.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper that delegates to another {@link FileSystemIO} instance with a hook implemented for retrieving a path from another location if it does not exist.
 *
 * @author peter
 */
public abstract class OverlayingFileSystemIO
        implements FileSystemIO
{

    private final FileSystemIO delegate;

    public OverlayingFileSystemIO( FileSystemIO delegate )
    {
        this.delegate = delegate;
    }

    protected final FileSystemIO getDelegate()
    {
        return delegate;
    }

    protected abstract void retrieve( char[] path )
            throws IOException;

    @Override
    public void close()
            throws IOException
    {
        delegate.close();
    }

    @Override
    public Path getBaseDirectory()
    {
        return delegate.getBaseDirectory();
    }

    @Override
    public boolean isTemporary()
    {
        return delegate.isTemporary();
    }

    @Override
    public boolean exists( char[] path )
            throws IOException
    {
        if( delegate.exists( path ) ) {
            return true;
        }

        retrieve( path );
        return delegate.exists( path );
    }

    @Override
    public void createDirectory( char[] path, FileAttribute<?>[] attrs )
            throws IOException
    {
        delegate.createDirectory( path, attrs );
    }

    @Override
    public InputStream newInputStream( char[] path )
            throws IOException
    {
        try {
            return delegate.newInputStream( path );
        }
        catch( FileNotFoundException ex ) {
            retrieve( path );
            return delegate.newInputStream( path );
        }
    }

    @Override
    public OutputStream newOutputStream( char[] path, OpenOption... options )
            throws IOException
    {
        return delegate.newOutputStream( path, options );
    }

    @Override
    public void deleteFile( char[] path, boolean exists )
            throws IOException
    {
        delegate.deleteFile( path, exists );
    }

    @Override
    public SeekableByteChannel newByteChannel( char[] path, Set<? extends OpenOption> options, FileAttribute<?>... attrs )
            throws IOException
    {
        return delegate.newByteChannel( path, options, attrs );
    }

    @Override
    public FileChannel newFileChannel( char[] path, Set<? extends OpenOption> options, FileAttribute<?>... attrs )
            throws IOException
    {
        return delegate.newFileChannel( path, options, attrs );
    }

    @Override
    public void copyFile( boolean b, char[] src, char[] dest, CopyOption... options )
            throws IOException
    {
        // This will retrieve source as necessary
        exists( src );
        delegate.copyFile( b, src, dest, options );
    }

    @Override
    public BasicFileAttributes getAttributes( char[] path )
            throws IOException
    {
        return delegate.getAttributes( path );
    }

    @Override
    public BasicFileAttributeView getAttributeView( char[] path )
    {
        return delegate.getAttributeView( path );
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream( char[] path, DirectoryStream.Filter<? super Path> filter )
            throws IOException
    {
        return delegate.newDirectoryStream( path, filter );
    }

    @Override
    public void expire()
    {
        delegate.expire();
    }

    public static abstract class Synchronous
            extends OverlayingFileSystemIO
    {

        private final Lock lock = new ReentrantLock();
        private final Map<String, Condition> conditions = new HashMap<>();

        private final ExecutorService executorService;

        public Synchronous( FileSystemIO delegate, ExecutorService executor )
        {
            super( delegate );
            this.executorService = executor;
        }

        protected final ExecutorService getExecutorService()
        {
            return executorService;
        }

        protected final void execute( String key, Callable<Void> t )
                throws IOException
        {
            lock.lock();
            try {
                Condition c = conditions.get( key );
                if( c == null ) {
                    c = lock.newCondition();
                    conditions.put( key, c );
                    lock.unlock();
                    try {
                        Future<Void> f = executorService.submit( t );
                        f.get();
                    }
                    finally {
                        lock.lock();
                        c.signalAll();
                        conditions.remove( key );
                    }
                }
                else {
                    c.await();
                }
            }
            catch( InterruptedException ex ) {
                throw new IOException( ex );
            }
            catch( ExecutionException ex ) {
                Throwable tr = ex.getCause();
                if( tr instanceof IOException ) {
                    throw (IOException) tr;
                }
                if( tr instanceof UncheckedIOException ) {
                    throw ((UncheckedIOException) tr).getCause();
                }
                else {
                    throw new IOException( tr );
                }
            }
            finally {
                lock.unlock();
            }

        }

        @Override
        protected final void retrieve( char[] path )
                throws IOException
        {
            if( path == null || path.length == 0 ) {
                throw new FileNotFoundException( "/" );
            }
            String p = String.valueOf( path );
            execute( p, () -> {
                 retrievePath( p );
                 return null;
             } );
        }

        protected abstract void retrievePath( String path )
                throws IOException;

        @Override
        public void close()
                throws IOException
        {
            try {
                executorService.shutdownNow();

                lock.lock();
                try {
                    conditions.values().forEach( Condition::signalAll );
                }
                finally {
                    lock.unlock();
                }
            }
            finally {
                super.close();
            }
        }

    }
}
