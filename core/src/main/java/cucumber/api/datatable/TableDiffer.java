package cucumber.api.datatable;

import cucumber.deps.difflib.Delta;
import cucumber.deps.difflib.DiffUtils;
import cucumber.deps.difflib.Patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cucumber.api.datatable.DataTableDiff.DiffType;
import static java.util.AbstractMap.SimpleEntry;

class TableDiffer {

    private final DataTable from;
    private final DataTable to;

    TableDiffer(DataTable fromTable, DataTable toTable) {
        checkColumns(fromTable, toTable);
        this.from = fromTable;
        this.to = toTable;
    }

    private void checkColumns(DataTable a, DataTable b) {
        if (a.topCells().size() != b.topCells().size() && !b.topCells().isEmpty()) {
            throw new IllegalArgumentException("Tables must have equal number of columns:\n" + a + "\n" + b);
        }
    }

    void calculateDiffs() throws TableDiffException {
        Patch patch = DiffUtils.diff(getDiffableRows(from), getDiffableRows(to));
        List<Delta> deltas = patch.getDeltas();
        if (!deltas.isEmpty()) {
            Map<Integer, Delta> deltasByLine = createDeltasByLine(deltas);
            throw new TableDiffException(from, to, createTableDiff(deltasByLine));
        }
    }


    private static List<DiffableRow> getDiffableRows(DataTable raw) {
        List<DiffableRow> result = new ArrayList<DiffableRow>();
        for (List<String> row : raw.raw()) {
            result.add(new DiffableRow(row, row));
        }
        return result;
    }

    void calculateUnorderedDiffs() throws TableDiffException {
        boolean isDifferent = false;
        List<SimpleEntry<List<String>, DiffType>> diffTableRows = new ArrayList<SimpleEntry<List<String>, DiffType>>();

        ArrayList<List<String>> extraRows = new ArrayList<List<String>>();

        // 1. add all "to" row in extra table
        // 2. iterate over "from", when a common row occurs, remove it from extraRows
        // finally, only extra rows are kept and in same order that in "to".
        extraRows.addAll(to.raw());

        for (List<String> row : from.raw()) {
            if (!to.raw().contains(row)) {
                diffTableRows.add(
                    new SimpleEntry<List<String>, DiffType>(row, DiffType.DELETE));
                isDifferent = true;
            } else {
                diffTableRows.add(
                    new SimpleEntry<List<String>, DiffType>(row, DiffType.NONE));
                extraRows.remove(row);
            }
        }

        for (List<String> cells : extraRows) {
            diffTableRows.add(
                new SimpleEntry<List<String>, DiffType>(cells, DiffType.INSERT));
            isDifferent = true;
        }

        if (isDifferent) {
            throw new TableDiffException(from, to, DataTableDiff.create(diffTableRows));
        }
    }

    private Map<Integer, Delta> createDeltasByLine(List<Delta> deltas) {
        Map<Integer, Delta> deltasByLine = new HashMap<Integer, Delta>();
        for (Delta delta : deltas) {
            deltasByLine.put(delta.getOriginal().getPosition(), delta);
        }
        return deltasByLine;
    }

    private DataTableDiff createTableDiff(Map<Integer, Delta> deltasByLine) {
        List<SimpleEntry<List<String>, DiffType>> diffTableRows = new ArrayList<SimpleEntry<List<String>, DiffType>>();
        List<List<String>> rows = from.raw();
        for (int i = 0; i < rows.size(); i++) {
            Delta delta = deltasByLine.get(i);
            if (delta == null) {
                diffTableRows.add(new SimpleEntry<List<String>, DiffType>(from.raw().get(i), DiffType.NONE));
            } else {
                addRowsToTableDiff(diffTableRows, delta);
                // skipping lines involved in a delta
                if (delta.getType() == Delta.TYPE.CHANGE || delta.getType() == Delta.TYPE.DELETE) {
                    i += delta.getOriginal().getLines().size() - 1;
                } else {
                    diffTableRows.add(new SimpleEntry<List<String>, DiffType>(from.raw().get(i), DiffType.NONE));
                }
            }
        }
        // Can have new lines at end
        Delta remainingDelta = deltasByLine.get(rows.size());
        if (remainingDelta != null) {
            addRowsToTableDiff(diffTableRows, remainingDelta);
        }
        return DataTableDiff.create(diffTableRows);
    }

    private void addRowsToTableDiff(List<SimpleEntry<List<String>, DiffType>> diffTableRows, Delta delta) {
        markChangedAndDeletedRowsInOriginalAsMissing(diffTableRows, delta);
        markChangedAndInsertedRowsInRevisedAsNew(diffTableRows, delta);
    }

    private void markChangedAndDeletedRowsInOriginalAsMissing(List<SimpleEntry<List<String>, DiffType>> diffTableRows, Delta delta) {
        List<DiffableRow> deletedLines = (List<DiffableRow>) delta.getOriginal().getLines();
        for (DiffableRow row : deletedLines) {
            diffTableRows.add(new SimpleEntry<List<String>, DiffType>(row.row, DiffType.DELETE));
        }
    }

    private void markChangedAndInsertedRowsInRevisedAsNew(List<SimpleEntry<List<String>, DiffType>> diffTableRows, Delta delta) {
        List<DiffableRow> insertedLines = (List<DiffableRow>) delta.getRevised().getLines();
        for (DiffableRow row : insertedLines) {
            diffTableRows.add(new SimpleEntry<List<String>, DiffType>(row.row, DiffType.INSERT));
        }
    }
}