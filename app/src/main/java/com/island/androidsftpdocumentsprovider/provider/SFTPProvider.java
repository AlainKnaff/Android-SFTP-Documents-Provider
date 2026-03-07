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

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.StrictMode;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import com.island.util.ErrorNotification;
import com.island.androidsftpdocumentsprovider.account.TheDatabase;
import com.island.androidsftpdocumentsprovider.account.Dao;
import com.island.androidsftpdocumentsprovider.account.Account;
import com.island.sftp.SFTP;
import com.island.sftp.SftpFile;
import com.island.sftp.SftpService;

import lu.knaff.alain.saf_sftp.R;

public class SFTPProvider extends DocumentsProvider
{
    public static final String TAG="SFTPDocumentsProvider";
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

    private Map<Uri,RefreshableCursor> cursors = new HashMap<>();

    @SuppressWarnings("LambdaLast")
    public void registerCursor(RefreshableCursor cursor, Uri documentId) {
	cursors.put(documentId, cursor);
    }

    @SuppressWarnings("LambdaLast")
    public void unregisterCursor(RefreshableCursor cursor, Uri documentId) {
	cursors.remove(documentId, cursor);
    }

    private void refreshCursorFor(Uri documentId)
        throws IOException
    {
        RefreshableCursor mc = cursors.get(documentId);
        if(mc != null) {
            mc.onChange(false);
        }
    }

    private void refreshCursorForParent(Uri documentId)
        throws IOException
    {
        String uriStr = documentId.toString();
        int idx = uriStr.lastIndexOf('/');
        if(idx >= 0)
            uriStr = uriStr.substring(0, idx);
        Log.d(TAG, "Parent dir="+uriStr);
        refreshCursorFor(Uri.parse(uriStr));
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
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider queryRoots %s",Arrays.toString(projection)));
	try {
	    MatrixCursor result=new MatrixCursor(resolveRootProjection(projection));
	    List<Account> accounts=dao.getAllAccounts();
	    for(Account account:accounts) {
		Uri uri=SFTP.parseUri(account.getName());
		MatrixCursor.RowBuilder row=result.newRow();
		row.add(Root.COLUMN_ROOT_ID,uri.toString());
		String documentId=uri.toString();
		String directory = account.getDirectory();
		if(directory == null || directory.isEmpty())
		    directory="/";
		documentId+=directory;
		row.add(Root.COLUMN_DOCUMENT_ID,documentId);
		int icon=R.drawable.ic_launcher;
		row.add(Root.COLUMN_ICON,icon);
		row.add(Root.COLUMN_FLAGS,
			Root.FLAG_SUPPORTS_CREATE |
			Root.FLAG_SUPPORTS_IS_CHILD);
		String title=getContext().getString(R.string.sftp);
		row.add(Root.COLUMN_TITLE,title);
		row.add(Root.COLUMN_SUMMARY,uri.getAuthority());
	    }
	    return result;
	} catch(Exception e) {
	    throw exception(e,"QueryRoots");
	}
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
	cancelStrictMode();
	Log.d(TAG, String.format("isChildDocument: parentDocumentId=%s, documentId=%s", parentDocumentId, documentId));
	final String parentUri = parentDocumentId;
	final String childUri = documentId;
	return childUri.startsWith(parentUri);
    }

    @Override
    public Cursor queryDocument(String uri,String[]projection)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider queryDocument %s %s",uri,Arrays.toString(projection)));
	try {
	    Objects.requireNonNull(uri);
	    MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
	    Uri documentId=Uri.parse(uri);
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
	    throw exception(e,"QueryDocument",uri);
	}
    }

    @Override
    public Cursor queryChildDocuments(String parentUri,
				      String[]projection,
				      String sortOrder)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider queryChildDocuments %s %s %s",parentUri,Arrays.toString(projection),Arrays.toString(projection)));
	try {
	    Objects.requireNonNull(parentUri);
	    Uri parentDocumentId=Uri.parse(parentUri);
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
	    throw exception(e,"QueryChildDocuments",parentUri);
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(String uri,String mode,CancellationSignal signal)throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider openDocument %s %s %s",uri,mode,signal));
	try {
	    Objects.requireNonNull(uri);
	    Objects.requireNonNull(mode);
	    final Uri documentId=Uri.parse(uri);
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
	    throw exception(e,"openDocument",uri);
	}
    }

    @Override
    public String createDocument(String parentUri,
				 String mimeType,
				 String displayName)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider createDocument %s %s %s",parentUri,mimeType,displayName));
	try {
	    Objects.requireNonNull(parentUri);
	    Objects.requireNonNull(mimeType);
	    Objects.requireNonNull(displayName);
	    Uri parentDocumentId=Uri.parse(parentUri);
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
                Uri[] documentId = new Uri[1];
                SftpFile file = uniqueFile(sftp,parent,displayName,
                                           documentId);
                if(Document.MIME_TYPE_DIR.equals(mimeType)) {
                    sftp.mkdirs(file);
                    refreshCursorFor(parentDocumentId);
                } else
                    sftp.newFile(file);
                return documentId[0].toString();
	    } catch(SocketException e) {
		    remove(sftp);
		    throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"CreateDocument",parentUri);
	}
    }
    @Override
    public void deleteDocument(String uri)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider deleteDocument %s",uri));
	try {
	    Objects.requireNonNull(uri);
	    Uri documentId=Uri.parse(uri);
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
	    throw exception(e,"DeleteDocument",uri);
	}
    }

    @Override
    public String getDocumentType(String uri)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider getDocumentType %s",uri));
	try {
	    Objects.requireNonNull(uri);
	    Uri documentId=Uri.parse(uri);
	    SFTP sftp=getSFTP(documentId);
	    try {
		String mimeType=sftp.getMimeType(sftp.getFile(documentId));
		return mimeType;
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"GetDocumentType",uri);
	}
    }

    @Override
    public String renameDocument(String uri, String displayName)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider renameDocument %s %s",uri,displayName));
	try {
	    Objects.requireNonNull(uri);
	    Objects.requireNonNull(displayName);
	    Uri documentId=Uri.parse(uri);
	    SFTP sftp=getSFTP(documentId);
	    try {
		File source=sftp.getFile(documentId);
		File parent=source.getParentFile();
		File destination=uniqueFile(sftp,parent,displayName);
		sftp.renameTo(source,destination);
		refreshCursorForParent(documentId);
		return sftp.getUri(destination).toString();
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"RenameDocument",uri);
	}
    }

    @Override
    public String moveDocument(String sourceUri,
			       String sourceParentUri,
			       String targetParentUri)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider moveDocument %s %s %s",sourceUri,sourceParentUri,targetParentUri));
	try {
	    Objects.requireNonNull(sourceUri);
	    Objects.requireNonNull(sourceParentUri);
	    Objects.requireNonNull(targetParentUri);
	    Uri sourceDocumentId=Uri.parse(sourceUri);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    File source=sftp.getFile(sourceDocumentId);
	    try {
		File destination=uniqueFile(sftp,sftp.getFile(Uri.parse(targetParentUri)),source.getName());
		sftp.renameTo(source,destination);
		refreshCursorForParent(sourceDocumentId);
		return sftp.getUri(destination).toString();
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"MoveDocument",sourceUri,targetParentUri);
	}
    }

    @Override
    public String copyDocument(String sourceUri,String targetParentUri)
	throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider copyDocument %s %s",sourceUri,targetParentUri));
	try {
	    Objects.requireNonNull(sourceUri);
	    Objects.requireNonNull(targetParentUri);
	    Uri sourceDocumentId=Uri.parse(sourceUri);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    File source=sftp.getFile(sourceDocumentId);
	    try {
                Uri targetDirId = Uri.parse(targetParentUri);
		SftpFile destination=uniqueFile(sftp,sftp.getFile(targetDirId),
                                                source.getName());
		sftp.copy(source,destination);
		refreshCursorForParent(sourceDocumentId);
		refreshCursorForParent(targetDirId);
		return sftp.getUri(destination).toString();
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"CopyDocument",sourceUri,targetParentUri);
	}
    }

    private static String[]resolveDocumentProjection(String[]projection) {
	if(projection==null)return DEFAULT_DOCUMENT_PROJECTION;
	else return projection;
    }

    private static String[]resolveRootProjection(String[]projection) {
	if(projection==null)return DEFAULT_ROOT_PROJECTION;
	else return projection;
    }

    public static String getToken(Context context,Uri documentId)
	throws IOException
    {
	return getAccountInfo(context, documentId).getPassword();
    }

    public static Account getAccountInfo(Context context,Uri documentId)
	throws IOException
    {
	Objects.requireNonNull(context);
	Objects.requireNonNull(documentId);
	Dao dao = TheDatabase.getDao(context);
	String accountName=documentId.getAuthority();
	Account account = dao.readAccountByName(accountName);
	if(account == null) {
	    throw new FileNotFoundException(documentId.toString());
	}
	return account;
    }

    private SFTP getSFTP(Uri documentId)
	throws IOException
    {
        return getSFTP(documentId, false);
    }

    private SFTP getSFTP(Uri documentId, boolean needsFresh)
	throws IOException
    {
	assert documentId!=null;
	SftpService.start(getContext());
	SFTP sftp=null;
        Set<SFTP> toRemove = new HashSet<>();
        for(SFTP connection:connections) {
            if(connection.uri.getAuthority().equals(documentId.getAuthority())){
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

    private SFTP createSftp(Uri documentId)
	throws IOException
    {
	Account account = getAccountInfo(getContext(), documentId);
        try {
            return new SFTP(getContext(), documentId, account);
        } catch(ConnectException e) {
            ErrorNotification.sendNotification(getContext(),
                                               String.valueOf(documentId),
                                               e);
            throw e;
        }
    }

    private void putFileInfo(MatrixCursor.RowBuilder row, Uri uri) {
        String name = uri.getLastPathSegment();
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
	    String documentId=sftp.getUri(file).toString();
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
                                Uri[] documentIdP)
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
            Uri documentId=sftp.getUri(new File(parent, base+seq+extension));
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
