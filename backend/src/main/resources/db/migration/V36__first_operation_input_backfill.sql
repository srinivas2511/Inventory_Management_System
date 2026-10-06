-- Orders released before the release step set the first operation's input_qty left that operation at 0, so no
-- output could ever be recorded on it. Give each such order's first operation the order's planned quantity
-- (DESIGN 4.7: sequence 1 input = order planned quantity). Only untouched operations are changed: nothing recorded
-- yet and no input assigned, on orders that are still open.
UPDATE production_operations op
   SET input_qty = po.planned_qty
  FROM production_orders po
 WHERE op.production_order_id = po.id
   AND po.status IN ('RELEASED', 'IN_PROGRESS')
   AND op.input_qty = 0
   AND op.good_qty = 0 AND op.rejected_qty = 0 AND op.scrap_qty = 0
   AND op.sequence_no = (SELECT MIN(o2.sequence_no) FROM production_operations o2
                          WHERE o2.production_order_id = po.id);
