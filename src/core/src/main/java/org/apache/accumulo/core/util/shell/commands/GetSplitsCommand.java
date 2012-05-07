/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.core.util.shell.commands;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.KeyExtent;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.util.ColumnFQ;
import org.apache.accumulo.core.util.TextUtil;
import org.apache.accumulo.core.util.shell.Shell;
import org.apache.accumulo.core.util.shell.Shell.Command;
import org.apache.accumulo.core.util.shell.Shell.PrintFile;
import org.apache.accumulo.core.util.shell.Shell.PrintLine;
import org.apache.accumulo.core.util.shell.Shell.PrintShell;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.io.Text;

public class GetSplitsCommand extends Command {
  
  private Option outputFileOpt, maxSplitsOpt, base64Opt, verboseOpt, tableOpt;
  
  @Override
  public int execute(String fullCommand, CommandLine cl, final Shell shellState) throws IOException, AccumuloException, AccumuloSecurityException,
      TableNotFoundException {
    
    String tableName;
    
    if (cl.hasOption(tableOpt.getOpt())) {
      tableName = cl.getOptionValue(tableOpt.getOpt());
      if (!shellState.getConnector().tableOperations().exists(tableName))
        throw new TableNotFoundException(null, tableName, null);
    }
    
    else {
      shellState.checkTableState();
      tableName = shellState.getTableName();
    }
    
    final String outputFile = cl.getOptionValue(outputFileOpt.getOpt());
    final String m = cl.getOptionValue(maxSplitsOpt.getOpt());
    final int maxSplits = m == null ? 0 : Integer.parseInt(m);
    final boolean encode = cl.hasOption(base64Opt.getOpt());
    final boolean verbose = cl.hasOption(verboseOpt.getOpt());
    
    PrintLine p = outputFile == null ? new PrintShell(shellState.getReader()) : new PrintFile(outputFile);
    
    try {
      if (!verbose) {
        for (Text row : maxSplits > 0 ? shellState.getConnector().tableOperations().getSplits(tableName, maxSplits) : shellState.getConnector()
            .tableOperations().getSplits(tableName)) {
          p.print(encode(encode, row));
        }
      } else {
        final Scanner scanner = shellState.getConnector().createScanner(Constants.METADATA_TABLE_NAME, Constants.NO_AUTHS);
        ColumnFQ.fetch(scanner, Constants.METADATA_PREV_ROW_COLUMN);
        final Text start = new Text(shellState.getConnector().tableOperations().tableIdMap().get(tableName));
        final Text end = new Text(start);
        end.append(new byte[] {'<'}, 0, 1);
        scanner.setRange(new Range(start, end));
        for (Iterator<Entry<Key,Value>> iterator = scanner.iterator(); iterator.hasNext();) {
          final Entry<Key,Value> next = iterator.next();
          if (Constants.METADATA_PREV_ROW_COLUMN.hasColumns(next.getKey())) {
            KeyExtent extent = new KeyExtent(next.getKey().getRow(), next.getValue());
            final String pr = encode(encode, extent.getPrevEndRow());
            final String er = encode(encode, extent.getEndRow());
            final String line = String.format("%-26s (%s, %s%s", obscuredTabletName(extent), pr == null ? "-inf" : pr, er == null ? "+inf" : er,
                er == null ? ") Default Tablet " : "]");
            p.print(line);
          }
        }
      }
      
    } finally {
      p.close();
    }
    
    return 0;
  }
  
  private static String encode(boolean encode, Text text) {
    if (text == null)
      return null;
    return encode ? new String(Base64.encodeBase64(TextUtil.getBytes(text))) : text.toString();
  }
  
  private static String obscuredTabletName(KeyExtent extent) {
    MessageDigest digester;
    try {
      digester = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    if (extent.getEndRow() != null && extent.getEndRow().getLength() > 0) {
      digester.update(extent.getEndRow().getBytes(), 0, extent.getEndRow().getLength());
    }
    return new String(Base64.encodeBase64(digester.digest()));
  }
  
  @Override
  public String description() {
    return "retrieves the current split points for tablets in the current table";
  }
  
  @Override
  public int numArgs() {
    return 0;
  }
  
  @Override
  public Options getOptions() {
    Options opts = new Options();
    
    outputFileOpt = new Option("o", "output", true, "local file to write the splits to");
    outputFileOpt.setArgName("file");
    
    maxSplitsOpt = new Option("m", "max", true, "maximum number of splits to return (evenly spaced)");
    maxSplitsOpt.setArgName("num");
    
    base64Opt = new Option("b64", "base64encoded", false, "encode the split points");
    
    verboseOpt = new Option("v", "verbose", false, "print out the tablet information with start/end rows");
    
    tableOpt = new Option(Shell.tableOption, "tableName", true, "table to get splits for");
    tableOpt.setArgName("table");
    tableOpt.setRequired(false);
    
    opts.addOption(outputFileOpt);
    opts.addOption(maxSplitsOpt);
    opts.addOption(base64Opt);
    opts.addOption(verboseOpt);
    opts.addOption(tableOpt);
    
    return opts;
  }
}
