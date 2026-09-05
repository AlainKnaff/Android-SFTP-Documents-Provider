package com.island.androidsftpdocumentsprovider.provider;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.SocketException;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.StrictMode;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import com.island.util.ErrorNotification;
import com.island.androidsftpdocumentsprovider.account.TheDatabase;
import com.island.androidsftpdocumentsprovider.account.Dao;
import com.island.androidsftpdocumentsprovider.account.Account;
import com.island.sftp.SFTP;
import static com.island.sftp.SFTP.normalize;
import com.island.sftp.SftpFile;
import com.island.sftp.SftpService;

import lu.knaff.alain.saf_sftp.R;

public class SFTPProvider extends DocumentsProvider
{
     static final String TAG="SFTPProvider";
    private static final String[]DEFAULT_ROOT_PROJECTION=
    {Root.COLUMN_ROOT_ID,Root.COLUMN_FLAGS,Root.COLUMN_ICON,Root.COLUMN_TITLE,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_SUMMARY};
    private static final String[]DEFAULT_DOCUMENT_PROJECTION= {
	Document.COLUMN_DOCUMENT_ID,
	Document.COLUMN_SIZE,
	Document.COLUMN_DISPLAY_NAME,
	Document.COLUMN_LAST_MODIFIED,
	Document.COLUMN_MIME_TYPE,
	Document.COLUMN_FLAGS
    };
    private final Set<SFTP>connections=new HashSet<>();


    private final static Set<String> uploadingFiles = new CopyOnWriteArraySet<>();
    private Dao dao;

    private final Map<String,RefreshableCursor> cursors = new HashMap<>();

    public void registerCursor(@NonNull String documentId,
			       @NonNull RefreshableCursor cursor) {
	cursors.put(documentId, cursor);
    }

    public void unregisterCursor(@NonNull String documentId,
				 @NonNull RefreshableCursor cursor) {
	cursors.remove(documentId, cursor);
    }

    private void refreshCursorFor(String documentId)
        throws IOException
    {
        RefreshableCursor mc = cursors.get(documentId);
        if(mc != null) {
            mc.onChange(false);
        }
    }

    private void refreshCursorForParent(String documentId)
        throws IOException
    {
        int idx = documentId.lastIndexOf('/');
        if(idx >= 0)
            documentId = documentId.substring(0, idx);
        refreshCursorFor(documentId);
    }

    @Override
    public boolean onCreate()
    {
	dao = TheDatabase.getDao(getContext());
	return true;
    }
    @Override
    public Cursor queryRoots(String[]projection)throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("queryRoots %s",Arrays.toString(projection)));
	try {
	    MatrixCursor result=new MatrixCursor(resolveRootProjection(projection));
	    List<Account> accounts=dao.getAllVisibleAccounts();
	    for(Account account:accounts) {
		String documentId=SFTP.accountToDocumentId(account);
		String directory = account.getDirectory();
		if(directory == null || directory.isEmpty())
		    directory="/";
		if(directory.length() > 1)
		    directory = directory.replaceAll("/$","");
		MatrixCursor.RowBuilder row=result.newRow();
		row.add(Root.COLUMN_ROOT_ID, documentId);
		row.add(Root.COLUMN_DOCUMENT_ID, documentId+directory);
		row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
		row.add(Root.COLUMN_FLAGS,
			Root.FLAG_SUPPORTS_CREATE |
			Root.FLAG_SUPPORTS_IS_CHILD);
		row.add(Root.COLUMN_TITLE,
			account.getName().replaceAll(":22$",""));
		if(directory.length() > 1)
		    row.add(Root.COLUMN_SUMMARY, directory);
	    }
	    return result;
	} catch(Exception e) {
	    throw exception(e,"QueryRoots");
	}
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
	cancelStrictMode();
	parentDocumentId = normalize(parentDocumentId);
	documentId = normalize(documentId);
	Log.d(TAG, String.format("isChildDocument: parentDocumentId=%s, documentId=%s", parentDocumentId, documentId));
	if(!documentId.startsWith(parentDocumentId))
	    return false;
	int l = parentDocumentId.length();
	if(documentId.length()==l)
	    return true; // the 2 document ids have the same lenght => equal
	return documentId.charAt(l) == '/';
	// child document has indeed a slash immediately after
	// parent part.
    }

    @Override
    public Cursor queryDocument(String documentId,String[]projection)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("queryDocument %s %s",documentId,Arrays.toString(projection)));
	try {
	    Objects.requireNonNull(documentId);
	    MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
            boolean needSftp=false;
            if(projection != null) {
                for(String column: projection)
                    if(Document.COLUMN_DOCUMENT_ID.equals(column) ||
                       Document.COLUMN_SIZE.equals(column) ||
                       Document.COLUMN_LAST_MODIFIED.equals(column) ||
                       Document.COLUMN_MIME_TYPE.equals(column) ||
                       Document.COLUMN_FLAGS.equals(column)) {
                        needSftp=true;
                        break;
                    }
            } else
                needSftp=true;
            MatrixCursor.RowBuilder mc = result.newRow();
            if(needSftp) {
                SFTP sftp=getSFTP(documentId);
                putFileInfo(mc,sftp,sftp.getFile(documentId));
            } else
                putFileInfo(mc,documentId);
            return result;
	} catch(Exception e) {
	    throw exception(e,"QueryDocument",documentId);
	}
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId,
				      String[] projection,
				      String sortOrder)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("queryChildDocuments %s %s %s",parentDocumentId,Arrays.toString(projection),Arrays.toString(projection)));
	try {
	    Objects.requireNonNull(parentDocumentId);
	    SFTP sftp=getSFTP(parentDocumentId, true);
            SftpFile parent = sftp.getFile(parentDocumentId);
            return
                new DirectoryCursor(this,
                                    parentDocumentId,
                                    sftp)
                .start(()-> { var files = sftp.listFiles(parent);
                        connections.add(sftp);
                        return files;
                });
	} catch(Exception e) {
	    throw exception(e,"QueryChildDocuments",parentDocumentId);
        }
    }

    @Override
    public @NonNull ParcelFileDescriptor openDocument(@NonNull String documentId,
						      @NonNull String mode,
						      @Nullable CancellationSignal signal)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("openDocument %s %s %s",documentId,mode,signal));
	try {
	    Objects.requireNonNull(documentId);
	    Objects.requireNonNull(mode);
	    SFTP sftp=getSFTP(documentId, true);
	    SftpFile serverFile = sftp.getFile(documentId);

	    try {
		return Proxy.open(getContext(), sftp, serverFile, mode,
                                  s->connections.add(s));
	    } catch (Exception e) {
		Log.e(TAG, "Failed to open proxy file descriptor", e);
		// openProxyFileDescriptor can throw an exception without
		// invoking onRelease
		remove(sftp);
		throw new FileNotFoundException(serverFile.getPath());
	    }
	} catch(Exception e) {
	    throw exception(e,"openDocument",documentId);
	}
    }

    @Override
    public String createDocument(String parentDocumentId,
				 String mimeType,
				 String displayName)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("createDocument %s %s %s",parentDocumentId,mimeType,displayName));
	try {
	    Objects.requireNonNull(parentDocumentId);
	    Objects.requireNonNull(mimeType);
	    Objects.requireNonNull(displayName);
	    SFTP sftp=getSFTP(parentDocumentId);
	    try {
		String base;
		String extension;
		int dotIdx=displayName.lastIndexOf('.');
		if(dotIdx >= 0) {
		    base = displayName.substring(0, dotIdx);
		    extension = displayName.substring(dotIdx);
		} else {
		    base=displayName;
		    extension = "";
		}
		int cnt=0;
		SftpFile parent = sftp.getFile(parentDocumentId);
                String[] documentId = new String[1];
                SftpFile file = uniqueFile(sftp,parent,displayName,
                                           documentId);
                if(Document.MIME_TYPE_DIR.equals(mimeType)) {
                    sftp.mkdirs(file);
                    refreshCursorFor(parentDocumentId);
                } else
                    sftp.newFile(file);
                return documentId[0];
	    } catch(SocketException e) {
		    remove(sftp);
		    throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"CreateDocument",parentDocumentId);
	}
    }
    @Override
    public void deleteDocument(@NonNull String documentId)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("deleteDocument %s",documentId));
	try {
	    Objects.requireNonNull(documentId);
	    SFTP sftp=getSFTP(documentId);
	    try {
		sftp.delete(sftp.getFile(documentId));
                refreshCursorForParent(documentId);
	    }
	    catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"DeleteDocument",documentId);
	}
    }

    @Override
    public @NonNull String getDocumentType(@NonNull String documentId)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("getDocumentType %s",documentId));
	try {
	    Objects.requireNonNull(documentId);
	    SFTP sftp=getSFTP(documentId);
	    try {
		String mimeType=sftp.getMimeType(sftp.getFile(documentId));
		return mimeType;
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"GetDocumentType",documentId);
	}
    }

    @Override
    public @NonNull String renameDocument(@NonNull String documentId,
					  @NonNull String displayName)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("renameDocument %s %s",documentId,displayName));
	try {
	    Objects.requireNonNull(documentId);
	    Objects.requireNonNull(displayName);
	    SFTP sftp=getSFTP(documentId);
	    try {
		File source=sftp.getFile(documentId);
		File parent=source.getParentFile();
		File destination=uniqueFile(sftp,parent,displayName);
		sftp.renameTo(source,destination);
		refreshCursorForParent(documentId);
		return sftp.getDocumentId(destination);
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"RenameDocument",documentId);
	}
    }

    @Override
    public @NonNull String moveDocument(@NonNull String sourceDocumentId,
					@NonNull String sourceParentDocumentId,
					@NonNull String targetParentDocumentId)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("moveDocument %s %s %s",sourceDocumentId,sourceParentDocumentId,targetParentDocumentId));
	try {
	    Objects.requireNonNull(sourceDocumentId);
	    Objects.requireNonNull(sourceParentDocumentId);
	    Objects.requireNonNull(targetParentDocumentId);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    File source=sftp.getFile(sourceDocumentId);
	    try {
		File destination=uniqueFile(sftp,
					    sftp.getFile(targetParentDocumentId),
					    source.getName());
		sftp.renameTo(source,destination);
		refreshCursorForParent(sourceDocumentId);
		return sftp.getDocumentId(destination);
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"MoveDocument",sourceDocumentId,targetParentDocumentId);
	}
    }

    @Override
    public @NonNull String copyDocument(@NonNull String sourceDocumentId,
					@NonNull String targetParentDocumentId)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("copyDocument %s %s",sourceDocumentId,targetParentDocumentId));
	try {
	    Objects.requireNonNull(sourceDocumentId);
	    Objects.requireNonNull(targetParentDocumentId);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    File source=sftp.getFile(sourceDocumentId);
	    try {
		SftpFile destination=uniqueFile(sftp,
						sftp.getFile(targetParentDocumentId),
                                                source.getName());
		sftp.copy(source,destination);
		refreshCursorForParent(sourceDocumentId);
		refreshCursorForParent(targetParentDocumentId);
		return sftp.getDocumentId(destination);
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"CopyDocument",sourceDocumentId,targetParentDocumentId);
	}
    }

    @Override
    public @NonNull Path findDocumentPath(@Nullable String parentDocId,
					  @NonNull String childDocId)
            throws FileNotFoundException {

	// TODO: handle non-null parentDocId. Right now we have no test case
	if(parentDocId != null)
	    throw new UnsupportedOperationException("Non-null parentDocId not yet supported due to lack of test case");

	final String rootId = SFTP.documentIdToAccount(childDocId);

	Account account = dao.readAccountByName(rootId);
	String extendedRoot=rootId;
	if(account != null) {
	    extendedRoot+=account.getDirectory().replaceAll("/$","");
	}

	// Skip legacy sftp:// prefix
	int start = childDocId.indexOf(extendedRoot);
	if(start < 0)
	    throw new FileNotFoundException(childDocId+" not below configured start directory");

	// Split path along slashed
	List<String> children = new ArrayList<>();
	int i=start+extendedRoot.length(); // index of next slash
	int length = childDocId.length();
	while(i<length) {
	    i = childDocId.indexOf('/',i);
	    if(i==-1)
		break;
	    children.add( childDocId.substring(start,i));
	    i=i+1;
	}
	children.add(childDocId);
	return new Path(rootId, children);
    }

    private static @NonNull String[]
	resolveDocumentProjection(@Nullable String[]projection) {
	if(projection==null)
	    return DEFAULT_DOCUMENT_PROJECTION;
	else return projection;
    }

    protected static @NonNull String[]
	resolveRootProjection(@Nullable String[]projection) {
	if(projection==null)
	    return DEFAULT_ROOT_PROJECTION;
	else return projection;
    }

    public static @Nullable String getToken(@NonNull Context context,
					    @NonNull String documentId)
	throws IOException
    {
	return getAccountInfo(context, documentId).getPassword();
    }


    private static Account getAccountInfo(Context context, String documentId)
	throws IOException
    {
	Objects.requireNonNull(context);
	Objects.requireNonNull(documentId);
	Dao dao = TheDatabase.getDao(context);
	String accountName = SFTP.documentIdToAccount(documentId);
	Account account = dao.readAccountByName(accountName);
	if(account == null) {
	    throw new FileNotFoundException(documentId);
	}
	return account;
    }

    private SFTP getSFTP(String documentId)
	throws IOException
    {
        return getSFTP(documentId, false);
    }

    private SFTP getSFTP(String documentId, boolean needsFresh)
	throws IOException
    {
	assert documentId!=null;
	SftpService.start(getContext());
	SFTP sftp=null;
        Set<SFTP> toRemove = new HashSet<>();
        for(SFTP connection:connections) {
            if(SFTP.documentIdToAccount(connection.documentId)
	       .equals(SFTP.documentIdToAccount(documentId))){
                if(!connection.isConnected()) {
                    Log.d(TAG, "Connection closed, cleaning");
                    toRemove.add(connection);
                    continue;
                }
                sftp=connection;
                if(needsFresh)
                    // remove connection from pool while used elsewhere
                    connections.remove(sftp);
                break;
            }
        }
        connections.removeAll(toRemove);
        if(sftp==null) {
            sftp= createSftp(documentId);
            if(!needsFresh)
                connections.add(sftp);
        }
        return sftp;
    }

    private SFTP createSftp(String documentId)
	throws IOException
    {
	Account account = getAccountInfo(getContext(), documentId);
        try {
            return new SFTP(getContext(), documentId, account);
        } catch(ConnectException e) {
            ErrorNotification.sendNotification(getContext(),
                                               documentId,
                                               e);
            throw e;
        }
    }

    private void putFileInfo(MatrixCursor.RowBuilder row, String documentId) {
        String name = documentId.replaceAll("^.*/","");
        row.add(Document.COLUMN_DISPLAY_NAME,name);
    }

    private void putFileInfo(MatrixCursor.RowBuilder row, SFTP sftp,
                             SftpFile file)
	throws IOException
    {
	try {
	    int flags;
	    if(sftp.isDirectory(file))
		flags=Document.FLAG_DIR_SUPPORTS_CREATE;
	    else {
		flags=Document.FLAG_SUPPORTS_WRITE;
		row.add(Document.COLUMN_SIZE,sftp.length(file));
	    }
	    flags|=Document.FLAG_SUPPORTS_DELETE|
		Document.FLAG_SUPPORTS_COPY|
		Document.FLAG_SUPPORTS_MOVE|
		Document.FLAG_SUPPORTS_RENAME;
	    row.add(Document.COLUMN_FLAGS,flags);
	    String mimeType=sftp.getMimeType(file);
	    row.add(Document.COLUMN_MIME_TYPE,mimeType);
	    String name=file.getName();
	    row.add(Document.COLUMN_DISPLAY_NAME,name);
	    String documentId=sftp.getDocumentId(file);
	    row.add(Document.COLUMN_DOCUMENT_ID,documentId);
	    long lastModified=sftp.lastModified(file);
	    row.add(Document.COLUMN_LAST_MODIFIED,lastModified);
	} catch(SocketException e) {
	    remove(sftp);
	    throw e;
	}
    }

    private SftpFile uniqueFile(SFTP sftp, File parent, String displayName)
	throws IOException
    {
        return uniqueFile(sftp, parent, displayName, null);
    }

    private SftpFile uniqueFile(SFTP sftp, File parent, String displayName,
                                String[] documentIdP)
	throws IOException
    {
	assert sftp!=null;
	assert parent!=null;
	assert displayName!=null;

        String base;
        String extension;
        int dotIdx=displayName.lastIndexOf('.');
        if(dotIdx >= 0) {
            base = displayName.substring(0, dotIdx);
            extension = displayName.substring(dotIdx);
        } else {
            base=displayName;
            extension = "";
        }
        int cnt=0;
        while(true) {
            String seq;
            if(cnt==0)
                seq="";
            else
                seq="_"+cnt;
            String documentId=sftp.getDocumentId(new File(parent,
							  base+seq+extension));
            SftpFile file=sftp.getFile(documentId);
            try {
                sftp.lastModified(file);
                cnt++;
                continue;
            } catch(FileNotFoundException e) {
                if(documentIdP != null)
                    documentIdP[0] = documentId;
                return file;
            }
        }
    }

    private FileNotFoundException exception(Exception e,
					    String msg,
					    Object...args)
    {
	assert e!=null;
	assert msg!=null;
	assert args!=null;
	for(Object arg:args)msg+=" "+arg;
	Log.e(TAG,msg,e);
	FileNotFoundException exception=new FileNotFoundException(msg);
	exception.initCause(e);
	return exception;
    }

    private void remove(SFTP sftp)
    {
	assert sftp!=null;
	connections.remove(sftp);
	try {
	    sftp.close();
	} catch (IOException e) {
	    Log.e(TAG, "sftp close exception", e);
	}
    }

    private void cancelStrictMode() {
	// if an application directly opens a file on a root
	// supplied by this document provider from its UI
	// thread, the StrictMode attached to that thread
	// carries over to the document provider via binder,
	// preventing it to invoke network (for SFTP),
	// although it's really the calling app's fault. =>
	// cancel StrictMode. This is legitimate as we can't
	// really do anything about it, as Documents Provider
	// API is synchronous (return from same method, rather
	// than calling a callback when done). Any solution
	// other than canceling StrictMode would involve
	// cheating by handing processing off to another
	// thread, but then waiting for that thread, blocking
	// anyways
	StrictMode.ThreadPolicy gfgPolicy =
	    new StrictMode.ThreadPolicy.Builder()
	    .permitAll()
	    .build();
	StrictMode.setThreadPolicy(gfgPolicy);
    }
}
