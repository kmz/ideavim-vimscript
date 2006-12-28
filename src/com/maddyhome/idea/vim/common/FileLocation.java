package com.maddyhome.idea.vim.common;

/*
 * IdeaVim - A Vim emulator plugin for IntelliJ Idea
 * Copyright (C) 2003-2006 Rick Maddy
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;

public abstract class FileLocation
{
    protected FileLocation(int lline, int col, VirtualFile file)
    {
        this.line = lline;
        this.col = col;
        this.file = file;
        this.filename = file.getPath();
    }

    protected FileLocation(int lline, int col, String filename)
    {
        this.line = lline;
        this.col = col;
        this.filename = filename;
        this.file = null;
    }

    /**
     * Clears the mark indicating that it is no longer a valid mark
     */
    public void clear()
    {
        line = -1;
        col = -1;
        file = null;
        filename = null;
    }

    /**
     * Checks to see if the mark has been invalidated
     * @return true is invalid or clear, false if not
     */
    public boolean isClear()
    {
        return (line == -1 && col == -1);
    }

    /**
     * The mark's line
     * @return The mark's line
     */
    public int getLogicalLine()
    {
        return line;
    }

    /**
     * Updates the mark's lline
     * @param lline The new lline for the mark
     */
    public void setLogicalLine(int lline)
    {
        this.line = lline;
    }

    /**
     * The mark's column
     * @return The mark's columnn
     */
    public int getCol()
    {
        return col;
    }

    /**
     * The virtual file associated with this mark
     * @return The mark's virtual file
     */
    public VirtualFile getFile()
    {
        if (file == null)
        {
            file = LocalFileSystem.getInstance().findFileByPath(filename);
        }

        return file;
    }

    /**
     * Gets the filename the mark is associate with
     * @return The mark's filename
     */
    public String getFilename()
    {
        if (filename != null)
        {
            return filename;
        }
        else if (file != null)
        {
            return file.getPath();
        }
        else
        {
            return null;
        }
    }

    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("FileLocation");
        sb.append("{col=").append(col);
        sb.append(", line=").append(line);
        sb.append(", file=").append(file);
        sb.append(", filename='").append(filename).append('\'');
        sb.append('}');
        return sb.toString();
    }

    private int line;
    private int col;
    private VirtualFile file;
    private String filename;
}
