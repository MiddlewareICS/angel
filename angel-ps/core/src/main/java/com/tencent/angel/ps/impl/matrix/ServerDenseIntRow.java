/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.angel.ps.impl.matrix;

import com.tencent.angel.protobuf.generated.MLProtos.RowType;
import io.netty.buffer.ByteBuf;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * The class represent dense int row on parameter server.
 */

public class ServerDenseIntRow extends ServerRow {

  private final static Log LOG = LogFactory.getLog(ServerDenseIntRow.class);

  private byte[] dataBuffer;
  private IntBuffer data;

  public ServerDenseIntRow(int rowId, int startCol, int endCol, byte[] buffer) {
    super(rowId, startCol, endCol);
    int elemNum = endCol - startCol;
    this.dataBuffer = buffer;
    if (dataBuffer != null) {
      this.data = ByteBuffer.wrap(this.dataBuffer, 0, elemNum * 4).asIntBuffer();
    } else {
      this.data = null;
    }
  }

  public ServerDenseIntRow(int rowId, int startCol, int endCol) {
    this(rowId, startCol, endCol, new byte[(endCol - startCol) * 4]);
  }

  public ServerDenseIntRow() {
    this(0, 0, 0, null);
  }

  @Override
  public RowType getRowType() {
    return RowType.T_INT_DENSE;
  }

  @Override
  public void writeTo(DataOutputStream output) throws IOException {
    try {
      lock.readLock().lock();
      super.writeTo(output);
      output.write(dataBuffer, 0, dataBuffer.length);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void readFrom(DataInputStream input) throws IOException {
    try {
      lock.writeLock().lock();
      super.readFrom(input);
      int totalSize = (endCol - startCol) * 4;
      int readLen = 0;
      int size = 0;
      while (size < totalSize) {
        readLen = input.read(dataBuffer, size, (totalSize - size));
        size += readLen;
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public int size() {
    return dataBuffer.length / 4;
  }

  @Override
  public void update(RowType rowType, ByteBuf buf, int size) {
    try {
      lock.writeLock().lock();
      switch (rowType) {
        case T_INT_DENSE:
          updateIntDense(buf, size);
          break;
        case T_INT_SPARSE:
          updateIntSparse(buf, size);
          break;
        default:
          LOG.error("Invalid rowType to update ServerDenseIntRow!");
      }
      updateRowVersion();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void updateIntDense(ByteBuf buf, int size) {
    for (int colId = 0; colId < size; colId++) {
      data.put(colId, data.get(colId) + buf.readInt());
    }
  }

  private void updateIntSparse(ByteBuf buf, int size) {
    ByteBuf valueBuf = buf.slice(buf.readerIndex() + size * 4, size * 4);
    int columnId = 0;
    int value = 0;
    for (int i = 0; i < size; i++) {
      columnId = buf.readInt();
      value = data.get(columnId) + valueBuf.readInt();
      data.put(columnId, value);
    }
    buf.readerIndex(buf.readerIndex() + size * 4);
  }

  public IntBuffer getData() {
    return data;
  }

  @Override
  public void serialize(ByteBuf buf) {
    try {
      lock.readLock().lock();
      super.serialize(buf);
      buf.writeInt(endCol - startCol);
      buf.writeBytes(dataBuffer);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void deserialize(ByteBuf buf) {
    try {
      lock.writeLock().lock();
      super.deserialize(buf);
      int elemNum = buf.readInt();
      if(dataBuffer == null || dataBuffer.length != elemNum * 4){
        dataBuffer = new byte[elemNum * 4];
      }
      
      buf.readBytes(dataBuffer);
      this.data = ByteBuffer.wrap(dataBuffer, 0, elemNum * 4).asIntBuffer();
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public int bufferLen() {
    return super.bufferLen() + 4 + dataBuffer.length;
  }

  public void mergeTo(int[] dataArray) {
    try {
      lock.readLock().lock();
      //data.rewind();
      int size = endCol - startCol;
      for (int i = 0; i < size; i++) {
        dataArray[startCol + i] = data.get(i);
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  public byte[] getDataArray() {
    return dataBuffer;
  }
}
