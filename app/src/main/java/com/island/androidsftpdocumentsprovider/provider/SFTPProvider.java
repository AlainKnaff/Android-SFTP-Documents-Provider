package com.island.androidsftpdocumentsprovider.provider;

import java.util.Objects;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.SocketException;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.StrictMode;
import android.os.ParcelFileDescriptor;
import android.os.Looper;
import android.os.Handler;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import com.island.androidsftpdocumentsprovider.R;
import com.island.androidsftpdocumentsprovider.account.DBHandler;
import com.island.androidsftpdocumentsprovider.account.Account;
import com.island.sftp.SFTP;

import com.island.androidsftpdocumentsprovider.provider.UploadWorker;
import java.security.MessageDigest;

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
    public static final String SFTP_UPLOAD_POST = "com.island.androidsftpdocumentsprovider.provider.SFTPProvider.uploadPost";
    private final List<SFTP>connections=new ArrayList<>();


    private final static Set<String> uploadingFiles = new CopyOnWriteArraySet<>();
    private DBHandler dbHandler;

    @Override
    public boolean onCreate()
    {
	dbHandler = new DBHandler(getContext());
	ContextCompat
	    .registerReceiver(getContext(),new BroadcastReceiver() {
		    @Override
		    public void onReceive(Context context, Intent intent) {
			String uri = intent.getStringExtra("uri");
			Log.d(TAG, String.format("Current uploading files: %s, remove %s", uploadingFiles, uri));
			uploadingFiles.remove(uri);
		    }
		}, new IntentFilter(SFTP_UPLOAD_POST),
		ContextCompat.RECEIVER_NOT_EXPORTED);
	return true;
    }
    @Override
    public Cursor queryRoots(String[]projection)throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider queryRoots %s",Arrays.toString(projection)));
	try {
	    MatrixCursor result=new MatrixCursor(resolveRootProjection(projection));
	    List<Account> accounts=dbHandler.readAccounts();
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
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider queryDocuments %s %s",uri,Arrays.toString(projection)));
	try {
	    Objects.requireNonNull(uri);
	    MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
	    Uri documentId=Uri.parse(uri);
	    putFileInfo(result.newRow(),documentId);
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
	    MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
	    Uri parentDocumentId=Uri.parse(parentUri);
	    SFTP sftp=getSFTP(parentDocumentId);
	    try {
		File[]files=sftp.listFiles(SFTP.getFile(parentDocumentId));
		for(File file:files) {
		    putFileInfo(result.newRow(),sftp.getUri(file));
		}
	    } catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	    return result;
	} catch(Exception e) {
	    throw exception(e,"QueryChildDocuments",parentUri);
	}
    }

    public static String hash(final String base) {
	try {
	    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
	    final byte[] hash = digest.digest(base.getBytes("UTF-8"));
	    return Base64.encodeToString(hash, Base64.NO_WRAP|Base64.URL_SAFE);
	} catch(Exception e){
	    throw new RuntimeException(e);
	}
    }

    /**
     * Make cache file, but take into account server identity, and
     * server directory. However, rather than using server path as is,
     * make a secure hash. This helps with dealing with path length
     * issues, presence of ".." in path, as well as file names being
     * reused for directory names at a later time, while they are
     * still in client cache.
     */
    private File cacheFile(int accountId, File serverFile) {
	File directory = getContext().getCacheDir();
	String path=String.valueOf(accountId)+"/"+serverFile.getParent();
	directory=new File(directory, hash(path));
	if(!directory.isDirectory()){
	    directory.mkdirs();
	}
	return new File(directory, serverFile.getName());
    }

    @Override
    public ParcelFileDescriptor openDocument(String uri,String mode,CancellationSignal signal)throws FileNotFoundException
    {
	cancelStrictMode();
	Log.d(SFTPProvider.TAG,String.format("SFTPProvider openDocument %s %s %s",uri,mode,signal));
	try {
	    Objects.requireNonNull(uri);
	    Objects.requireNonNull(mode);
	    int accessMode=ParcelFileDescriptor.parseMode(mode);
	    boolean isWrite=(mode.indexOf('w')!=-1);
	    final Uri documentId=Uri.parse(uri);
	    SFTP sftp=getSFTP(documentId);
	    File serverFile = SFTP.getFile(documentId);
	    File cache=cacheFile(sftp.getId(), serverFile);
	    try {
		boolean isDownloadFile = true;
		long serverLastModified = sftp.lastModified(serverFile);
		if(cache.exists()){
		    if( uploadingFiles.contains(documentId.toString())){
			Log.d(TAG, String.format("File %s uploading, open cache file.", documentId.toString()));
			isDownloadFile = false;
		    }
		    if(cache.lastModified() == serverLastModified){
			Log.d(TAG, String.format("File %s is not modify, open cache file.", documentId.toString()));
			isDownloadFile = false;
		    }
		}

		if (isDownloadFile) {
		    sftp.get(serverFile, cache);
		}
		if(isWrite) {
		    Looper looper=getContext().getMainLooper();
		    UploadWorker req =
			UploadWorker.Prepare(getContext(), cache, documentId);

		    var ret= ParcelFileDescriptor
			.open(cache,accessMode,new Handler(looper),
			      exception -> {
				  Log.d(TAG, "File close: " + cache + ", file size: " + cache.length());
				  if(exception==null) {
				      asyncUpload(cache, documentId, req);
				  } else {
				      exception(exception,"OnCloseDocument");
				  }
			      });
		    req.waitForSetup(); // make sure service is foregrounded
			// before we return from the Binder call
		    return ret;
		} else {
		    return ParcelFileDescriptor.open(cache,accessMode);
		}
	    }
	    catch(SocketException e) {
		remove(sftp);
		throw e;
	    }
	} catch(Exception e) {
	    throw exception(e,"openDocument",uri);
	}
    }

    private void asyncUpload(File cacheFile, Uri documentId,
			     UploadWorker req) {
	uploadingFiles.add(documentId.toString());
	req.start();
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
		while(true) {
		    String seq;
		    if(cnt==0)
			seq="";
		    else
			seq="_"+cnt;
		    Uri documentId=sftp
			.getUri(new File(SFTP.getFile(parentDocumentId),
					 base+seq+extension));
		    File file=SFTP.getFile(documentId);
		    try {
			sftp.lastModified(file);
			cnt++;
			continue;
		    } catch(FileNotFoundException e) {
			// file does not exist yet => ok
		    }
		    if(Document.MIME_TYPE_DIR.equals(mimeType))
			sftp.mkdirs(file);
		    else
			sftp.newFile(file);
		    return documentId.toString();
		}
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
		sftp.delete(SFTP.getFile(documentId));
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
		String mimeType=sftp.getMimeType(SFTP.getFile(documentId));
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
		File source=SFTP.getFile(documentId);
		File parent=source.getParentFile();
		File destination=uniqueFile(sftp,parent,displayName);
		sftp.renameTo(source,destination);
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
	    File source=SFTP.getFile(sourceDocumentId);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    try {
		File destination=uniqueFile(sftp,SFTP.getFile(Uri.parse(targetParentUri)),source.getName());
		sftp.renameTo(source,destination);
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
	    File source=SFTP.getFile(sourceDocumentId);
	    SFTP sftp=getSFTP(sourceDocumentId);
	    try {
		File destination=uniqueFile(sftp,SFTP.getFile(Uri.parse(targetParentUri)),source.getName());
		sftp.copy(source,destination);
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
	DBHandler dbHandler = new DBHandler(context);
	String accountName=documentId.getAuthority();
	Account account = dbHandler.readAccountByName(accountName);
	if(account == null) {
	    throw new FileNotFoundException(documentId.toString());
	}
	return account;
    }

    private SFTP getSFTP(Uri documentId)
	throws IOException
    {
	assert documentId!=null;
	SFTP sftp=null;
	for(SFTP connection:connections) {
	    if(connection.uri.getAuthority().equals(documentId.getAuthority())){
		sftp=connection;
		break;
	    }
	}
	if(sftp==null) {
	    sftp= createSftp(documentId);
	    connections.add(sftp);
	}
	return sftp;
    }

    private SFTP createSftp(Uri documentId)
	throws IOException
    {
	Account account = getAccountInfo(getContext(), documentId);
	return new SFTP(getContext(), documentId,
			account.getPassword(), account.getId());
    }

    private void putFileInfo(MatrixCursor.RowBuilder row,Uri uri)
	throws IOException
    {
	assert row!=null;
	assert uri!=null;
	int flags;
	SFTP sftp=getSFTP(uri);
	try {
	    File file=SFTP.getFile(uri);
	    if(sftp.isDirectory(file))
		flags=Document.FLAG_DIR_SUPPORTS_CREATE;
	    else {
		flags=Document.FLAG_SUPPORTS_WRITE;
		row.add(Document.COLUMN_SIZE,sftp.length(file));
	    }
	    flags|=Document.FLAG_SUPPORTS_DELETE;
	    if(Build.VERSION.SDK_INT>=24)
		flags|= Document.FLAG_SUPPORTS_COPY|
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

    private File uniqueFile(SFTP sftp, File parent, String displayName)
	throws IOException
    {
	assert sftp!=null;
	assert parent!=null;
	assert displayName!=null;
	File destination=new File(parent,displayName);
	while(sftp.exists(destination)) {
	    int lastDot=displayName.lastIndexOf('.');
	    String name,extension;
	    if(lastDot>=0) {
		name=displayName.substring(0,lastDot);
		extension=displayName.substring(lastDot+1);
	    } else
		name=extension=null;
	    name+=" 2";
	    displayName=name+"."+extension;
	    destination=new File(parent,displayName);
	}
	return destination;
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
