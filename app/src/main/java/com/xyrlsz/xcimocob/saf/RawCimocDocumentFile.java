package com.xyrlsz.xcimocob.saf;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Created by Hiroshi on 2017/3/24.
 */

class RawCimocDocumentFile extends CimocDocumentFile {

    private File mFile;

    RawCimocDocumentFile(CimocDocumentFile parent, File file) {
        super(parent);
        mFile = file;
    }

    private static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }

        return "application/octet-stream";
    }

    private static boolean deleteContents(File dir) {
        // 使用基于栈的迭代后序遍历替代递归，避免深度递归导致的栈溢出
        // 同时减少方法调用开销
        LinkedList<File> stack = new LinkedList<>();
        LinkedList<File> deleteOrder = new LinkedList<>();
        stack.push(dir);

        while (!stack.isEmpty()) {
            File current = stack.pop();
            deleteOrder.push(current);
            File[] files = current.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        stack.push(file);
                    } else {
                        deleteOrder.push(file);
                    }
                }
            }
        }

        // 按后序遍历顺序删除（先子后父）
        boolean success = true;
        for (File file : deleteOrder) {
            if (file != dir && !file.delete()) {
                success = false;
            }
        }
        return success;
    }

    @Override
    public CimocDocumentFile createFile(String displayName) {
        File target = new File(mFile, displayName);
        if (!target.exists()) {
            try {
                if (!target.createNewFile()) {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return new RawCimocDocumentFile(this, target);
    }

    @Override
    public CimocDocumentFile createDirectory(String displayName) {
        final File target = new File(mFile, displayName);
        if (target.isDirectory() || target.mkdir()) {
            return new RawCimocDocumentFile(this, target);
        }
        return null;
    }

    @Override
    public Uri getUri() {
        return Uri.fromFile(mFile);
    }

    @Override
    public String getName() {
        return mFile.getName();
    }

    @Override
    public String getType() {
        if (!mFile.isDirectory()) {
            return getTypeForName(mFile.getName());
        }
        return null;
    }

    @Override
    public boolean isDirectory() {
        return mFile.isDirectory();
    }

    @Override
    public boolean isFile() {
        return mFile.isFile();
    }

    @Override
    public long length() {
        return mFile.length();
    }

    @Override
    public boolean canRead() {
        return mFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return mFile.canWrite();
    }

    @Override
    public boolean delete() {
        deleteContents(mFile);
        return mFile.delete();
    }

    @Override
    public boolean exists() {
        return mFile.exists();
    }

    @Override
    public InputStream openInputStream() throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(mFile));
    }

    @Override
    public List<CimocDocumentFile> listFiles(DocumentFileFilter filter, Comparator<? super CimocDocumentFile> comp) {
        final ArrayList<CimocDocumentFile> results = new ArrayList<>();
        final File[] files = mFile.listFiles();
        if (files != null) {
            for (File file : files) {
                CimocDocumentFile doc = new RawCimocDocumentFile(this, file);
                if (filter == null || filter.call(doc)) {
                    results.add(doc);
                }
            }
        }
        if (comp != null) {
            Collections.sort(results, comp);
        }
        return results;
    }

    @Override
    public CimocDocumentFile[] listFiles() {
        final File[] files = mFile.listFiles();
        final CimocDocumentFile[] results = new CimocDocumentFile[Objects.requireNonNull(files).length];
        for (int i = 0; i < files.length; ++i) {
            results[i] = new RawCimocDocumentFile(this, files[i]);
        }
        return results;
    }

    @Override
    public void refresh() {
    }

    @Override
    public CimocDocumentFile findFile(String displayName) {
        for (CimocDocumentFile file : listFiles()) {
            if (displayName.equals(file.getName())) {
                return file;
            }
        }
        return null;
    }

    @Override
    public boolean renameTo(String displayName) {
        final File target = new File(mFile.getParentFile(), displayName);
        if (mFile.renameTo(target)) {
            mFile = target;
            return true;
        } else {
            return false;
        }
    }

}
