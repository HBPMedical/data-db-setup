
CREATE OR REPLACE VIEW {{ view.name }}
  (
    {{{ table1.columns }}},
    {{{ table2.columnsNoId }}}
  )
  AS SELECT {{{ table1.qualifiedColumns }}},{{{ table2.qualifiedColumnsNoId }}}
     FROM {{ table1.name }}
     LEFT OUTER JOIN {{ table2.name }}
     ON ({{{ table1.qualifiedId }}} = {{{ table2.qualifiedId }}});
