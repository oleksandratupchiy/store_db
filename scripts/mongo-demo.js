// Run only in the dedicated lab2_demo database; never touches imported orders.
const demo = db.getSiblingDB('lab2_demo').order_history;
try {
const inserted = demo.updateOne({_id: -1}, {$setOnInsert: {
  customer: {id: 1, name: 'Demo customer'}, employee_id: 1,
  order_date: ISODate('2025-01-01T12:00:00Z'), total_amount: NumberDecimal('120.00'),
  items: [{id: 1, product_id: 1, name: 'Coffee', quantity: 1, unit_price: NumberDecimal('120.00')}],
  payments: [{id: 1, method: 'CARD', amount: NumberDecimal('120.00'), date: ISODate('2025-01-01T12:01:00Z')}]
}}, {upsert: true});
assert.eq(true, inserted.acknowledged, 'Upsert was not acknowledged');
const updated = demo.updateOne({_id: -1}, {$set: {demo_note: 'Updated read model example'}});
assert.eq(true, updated.acknowledged, 'Update was not acknowledged');
assert.eq(1, updated.matchedCount, 'Demo document not found for update');
const result = demo.findOne({_id: -1});
assert.eq('Updated read model example', result.demo_note, 'Update was not saved');
printjson(result);
print('Mongo demo PASS');
} catch (error) {
  print('Mongo demo failed: ' + error);
  quit(1);
}
