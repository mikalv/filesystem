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
package onl.area51.filesystem.ftp;

import java.util.Map;
import onl.area51.filesystem.ftp.client.FTPClient;
import onl.area51.filesystem.io.FileSystemIO;

/**
 *
 * @author peter
 */
public class AbstractFTPRetriever
{

    private final FileSystemIO delegate;
    private final Map<String, ?> env;
    private FTPClient ftpClient;

    public AbstractFTPRetriever( FileSystemIO delegate, Map<String, ?> env )
    {
        this.delegate = delegate;
        this.env = env;
    }

    protected synchronized FTPClient getFTPClient()
    {
        return ftpClient;
    }
}
